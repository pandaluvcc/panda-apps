package com.panda.gridtrading.service;

import com.panda.gridtrading.constants.GridConstants;
import com.panda.gridtrading.domain.GridLine;
import com.panda.gridtrading.domain.GridLineState;
import com.panda.gridtrading.domain.GridType;
import com.panda.gridtrading.domain.Strategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 网格服务
 * <p>
 * 负责网格价格计算、卖出价计算等核心逻辑
 */
@Service
@Slf4j
public class GridService {

    /**
     * 计算网格买入后的卖出价
     * <p>
     * 核心规则：
     * - 小网：MAX(成本+5%, 上一小网买入价)
     * - 中网：MAX(成本+15%, 锚点/上一中网买入价)
     * - 大网：MAX(成本+30%, 锚点/第9网买入价)
     *
     * @param strategy       策略
     * @param currentGridLine 当前网格线
     */
    public void updateSellPriceAfterBuy(Strategy strategy, GridLine currentGridLine) {
        GridType gridType = currentGridLine.getGridType();
        int currentLevel = currentGridLine.getLevel();
        BigDecimal actualBuyPrice = currentGridLine.getActualBuyPrice();

        if (actualBuyPrice == null) {
            log.warn("[updateSellPriceAfterBuy] 网格 level={} 的 actualBuyPrice 为空，跳过更新", currentLevel);
            return;
        }

        List<GridLine> allGridLines = strategy.getGridLines();
        allGridLines.sort((a, b) -> a.getLevel().compareTo(b.getLevel()));

        BigDecimal newSellPrice = calculateSellPrice(strategy, allGridLines, gridType, currentLevel, actualBuyPrice);

        if (newSellPrice != null) {
            currentGridLine.setSellPrice(newSellPrice);
            currentGridLine.setSellTriggerPrice(GridConstants.calculateSellTriggerPrice(newSellPrice));

            BigDecimal actualProfitRate = newSellPrice.subtract(actualBuyPrice)
                    .divide(actualBuyPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            log.info("[UPDATE-SELL-PRICE] level={}, sellPrice={}, 收益率={}%",
                    currentGridLine.getLevel(), newSellPrice, actualProfitRate.setScale(1, RoundingMode.HALF_UP));
        }
    }

    /**
     * 计算建议卖出价（供OCR导入后重置卖出价使用）
     * <p>
     * 当网格卖出后进入WAIT_BUY状态，需要按买入价重新计算建议卖出价
     *
     * @param strategy  策略
     * @param gridLine  网格线
     * @return 建议卖出价
     */
    public BigDecimal calculateSuggestedSellPrice(Strategy strategy, GridLine gridLine) {
        BigDecimal buyPrice = gridLine.getBuyPrice();
        if (buyPrice == null) {
            return null;
        }

        List<GridLine> allGridLines = strategy.getGridLines();
        allGridLines.sort((a, b) -> a.getLevel().compareTo(b.getLevel()));

        return calculateSellPrice(strategy, allGridLines, gridLine.getGridType(), gridLine.getLevel(), buyPrice);
    }

    /**
     * 根据网格类型计算卖出价
     */
    private BigDecimal calculateSellPrice(
            Strategy strategy,
            List<GridLine> allGridLines,
            GridType gridType,
            int currentLevel,
            BigDecimal actualBuyPrice) {

        BigDecimal minSellPrice = GridConstants.calculateMinSellPrice(actualBuyPrice, gridType);

        switch (gridType) {
            case SMALL:
                return calculateSmallGridSellPrice(allGridLines, currentLevel, actualBuyPrice, minSellPrice);
            case MEDIUM:
                return calculateMediumGridSellPrice(strategy, allGridLines, currentLevel, actualBuyPrice, minSellPrice);
            case LARGE:
                return calculateLargeGridSellPrice(strategy, allGridLines, currentLevel, actualBuyPrice, minSellPrice);
            default:
                return minSellPrice;
        }
    }

    /**
     * 计算小网卖出价
     */
    private BigDecimal calculateSmallGridSellPrice(
            List<GridLine> allGridLines,
            int currentLevel,
            BigDecimal actualBuyPrice,
            BigDecimal minSellPrice) {

        if (currentLevel == 1) {
            return minSellPrice;
        }

        // 找上一小网的有效买入价
        BigDecimal targetSellPrice = findPreviousGridBuyPrice(allGridLines, currentLevel, GridType.SMALL);

        // 收益最大化：MAX(成本+5%, 上一小网买入价)
        return maxPrice(minSellPrice, targetSellPrice);
    }

    /**
     * 计算中网卖出价
     */
    private BigDecimal calculateMediumGridSellPrice(
            Strategy strategy,
            List<GridLine> allGridLines,
            int currentLevel,
            BigDecimal actualBuyPrice,
            BigDecimal minSellPrice) {

        BigDecimal targetSellPrice;

        if (currentLevel == 5) {
            // 第1个中网：卖回 basePrice
            targetSellPrice = strategy.getBasePrice();
        } else {
            // 后续中网：卖回上一个中网的买入价
            targetSellPrice = findPreviousGridBuyPrice(allGridLines, currentLevel, GridType.MEDIUM);
            if (targetSellPrice == null) {
                targetSellPrice = strategy.getBasePrice();
            }
        }

        return maxPrice(minSellPrice, targetSellPrice);
    }

    /**
     * 计算大网卖出价
     */
    private BigDecimal calculateLargeGridSellPrice(
            Strategy strategy,
            List<GridLine> allGridLines,
            int currentLevel,
            BigDecimal actualBuyPrice,
            BigDecimal minSellPrice) {

        BigDecimal targetSellPrice;

        if (currentLevel == 10) {
            // 第1个大网：卖回 basePrice
            targetSellPrice = strategy.getBasePrice();
        } else {
            // 第2个大网：卖回第2个中网的买入价
            targetSellPrice = findNthGridBuyPrice(allGridLines, GridType.MEDIUM, 2);
            if (targetSellPrice == null) {
                targetSellPrice = strategy.getBasePrice();
            }
        }

        return maxPrice(minSellPrice, targetSellPrice);
    }

    /**
     * 找到前一个同类型网格的买入价
     */
    private BigDecimal findPreviousGridBuyPrice(List<GridLine> allGridLines, int currentLevel, GridType gridType) {
        for (int i = allGridLines.size() - 1; i >= 0; i--) {
            GridLine gl = allGridLines.get(i);
            if (gl.getLevel() < currentLevel && gl.getGridType() == gridType) {
                return gl.getActualBuyPrice() != null ? gl.getActualBuyPrice() : gl.getBuyPrice();
            }
        }
        return null;
    }

    /**
     * 找到第N个指定类型网格的买入价
     */
    private BigDecimal findNthGridBuyPrice(List<GridLine> allGridLines, GridType gridType, int n) {
        int count = 0;
        for (GridLine gl : allGridLines) {
            if (gl.getGridType() == gridType) {
                count++;
                if (count == n) {
                    return gl.getActualBuyPrice() != null ? gl.getActualBuyPrice() : gl.getBuyPrice();
                }
            }
        }
        return null;
    }

    /**
     * 取两个价格的最大值
     */
    private BigDecimal maxPrice(BigDecimal price1, BigDecimal price2) {
        if (price2 == null) {
            return price1.setScale(3, RoundingMode.HALF_UP);
        }
        return price1.compareTo(price2) > 0 ? price1 : price2;
    }

    /**
     * 重新计算后续网格的价格
     * <p>
     * 核心规则：
     * - 小网 buyPrice：上一小网 × 0.95
     * - 中网/大网 buyPrice：继承最近小网
     * - sellPrice：MAX(buyPrice × 目标收益率, 回撤参考价)
     *
     * @param strategy         策略
     * @param currentGridLine  当前触发更新的网格线
     * @param actualBuyPrice   实际买入价
     */
    public void recalculateSubsequentGrids(Strategy strategy, GridLine currentGridLine, BigDecimal actualBuyPrice) {
        List<GridLine> allGridLines = strategy.getGridLines();
        int currentLevel = currentGridLine.getLevel();
        GridType currentType = currentGridLine.getGridType();

        // 只有小网的买入价变化才会影响后续网格
        if (currentType != GridType.SMALL) {
            return;
        }

        allGridLines.sort((a, b) -> a.getLevel().compareTo(b.getLevel()));

        BigDecimal lastSmallBuyPrice = actualBuyPrice;
        BigDecimal lastSmallEffectiveBuyPrice = actualBuyPrice;
        BigDecimal lastMediumBuyPrice = null;
        BigDecimal secondMediumBuyPrice = null;
        int mediumCount = 0;

        // 先扫描当前网格之前的信息
        for (GridLine gl : allGridLines) {
            if (gl.getLevel() > currentLevel) {
                break;
            }

            if (gl.getGridType() == GridType.SMALL) {
                BigDecimal price = getEffectiveBuyPrice(gl);
                lastSmallBuyPrice = price;
                lastSmallEffectiveBuyPrice = price;
            }

            if (gl.getGridType() == GridType.MEDIUM) {
                mediumCount++;
                BigDecimal price = getEffectiveBuyPrice(gl);
                lastMediumBuyPrice = price;
                if (mediumCount == 2) {
                    secondMediumBuyPrice = price;
                }
            }
        }

        // 重算后续网格
        boolean foundCurrent = false;
        for (GridLine gridLine : allGridLines) {
            if (!foundCurrent) {
                if (gridLine.getLevel() == currentLevel) {
                    foundCurrent = true;
                }
                continue;
            }

            GridLineState currentState = gridLine.getState();
            boolean hasActualBuyPrice = gridLine.getActualBuyPrice() != null;

            // 已交易的网格：先用实际价格更新追踪变量，然后跳过价格更新
            if (hasActualBuyPrice) {
                // 更新追踪变量使用实际成交价格
                if (gridLine.getGridType() == GridType.SMALL) {
                    BigDecimal currentSmallPrice = gridLine.getActualBuyPrice();
                    lastSmallBuyPrice = currentSmallPrice;
                    lastSmallEffectiveBuyPrice = currentSmallPrice;
                } else if (gridLine.getGridType() == GridType.MEDIUM) {
                    mediumCount++;
                    BigDecimal currentMediumPrice = gridLine.getActualBuyPrice();
                    lastMediumBuyPrice = currentMediumPrice;
                    if (mediumCount == 2) {
                        secondMediumBuyPrice = currentMediumPrice;
                    }
                }
                log.info("[RECALC-SKIP] 网格 level={} 已交易，跳过价格更新", gridLine.getLevel());
                continue;
            }

            // 计算新价格
            BigDecimal newBuyPrice = null;
            BigDecimal newSellPrice = null;

            if (gridLine.getGridType() == GridType.SMALL) {
                newBuyPrice = lastSmallBuyPrice.multiply(GridConstants.DECREASE_FACTOR)
                        .setScale(3, RoundingMode.DOWN);

                BigDecimal minSellPrice = newBuyPrice.multiply(BigDecimal.ONE.add(GridConstants.SMALL_PROFIT_RATE));
                newSellPrice = maxPrice(minSellPrice, lastSmallEffectiveBuyPrice);

                // 计算完新价格后更新追踪变量，供后续网格使用
                lastSmallBuyPrice = newBuyPrice;
                lastSmallEffectiveBuyPrice = newBuyPrice;

            } else if (gridLine.getGridType() == GridType.MEDIUM) {
                newBuyPrice = lastSmallBuyPrice;

                BigDecimal minSellPrice = newBuyPrice.multiply(BigDecimal.ONE.add(GridConstants.MEDIUM_PROFIT_RATE));
                BigDecimal targetSellPrice;
                if (gridLine.getLevel() == 5) {
                    targetSellPrice = strategy.getBasePrice();
                } else {
                    targetSellPrice = lastMediumBuyPrice != null ? lastMediumBuyPrice : strategy.getBasePrice();
                }
                newSellPrice = maxPrice(minSellPrice, targetSellPrice);

                // 计算完新价格后更新追踪变量，供后续网格使用
                mediumCount++;
                lastMediumBuyPrice = newBuyPrice;
                if (mediumCount == 2) {
                    secondMediumBuyPrice = newBuyPrice;
                }

            } else { // LARGE
                newBuyPrice = lastSmallBuyPrice;

                BigDecimal minSellPrice = newBuyPrice.multiply(BigDecimal.ONE.add(GridConstants.LARGE_PROFIT_RATE));
                BigDecimal targetSellPrice;
                if (gridLine.getLevel() == 10) {
                    targetSellPrice = strategy.getBasePrice();
                } else {
                    targetSellPrice = secondMediumBuyPrice != null ? secondMediumBuyPrice : strategy.getBasePrice();
                }
                newSellPrice = maxPrice(minSellPrice, targetSellPrice);
            }

            // 根据状态更新字段
            updateGridLinePrices(gridLine, currentState, newBuyPrice, newSellPrice);
        }
    }

    /**
     * 获取网格的有效买入价
     */
    private BigDecimal getEffectiveBuyPrice(GridLine gridLine) {
        return gridLine.getActualBuyPrice() != null ? gridLine.getActualBuyPrice() : gridLine.getBuyPrice();
    }

    /**
     * 根据网格状态更新价格
     */
    private void updateGridLinePrices(GridLine gridLine, GridLineState state, BigDecimal newBuyPrice, BigDecimal newSellPrice) {
        if (state == GridLineState.WAIT_BUY) {
            gridLine.setBuyPrice(newBuyPrice);
            gridLine.setSellPrice(newSellPrice);
            gridLine.setBuyTriggerPrice(GridConstants.calculateBuyTriggerPrice(newBuyPrice));
            gridLine.setSellTriggerPrice(GridConstants.calculateSellTriggerPrice(newSellPrice));

            log.info("[RECALC] 更新网格 level={}, state=WAIT_BUY, 新买入价={}, 新卖出价={}",
                    gridLine.getLevel(), newBuyPrice, newSellPrice);

            recalculateGridLineAmounts(gridLine, newBuyPrice, newSellPrice);

        } else if (state == GridLineState.BOUGHT) {
            gridLine.setSellPrice(newSellPrice);
            gridLine.setSellTriggerPrice(GridConstants.calculateSellTriggerPrice(newSellPrice));

            log.info("[RECALC] 更新网格 level={}, state=BOUGHT, 保持买入价={}, 新卖出价={}",
                    gridLine.getLevel(), gridLine.getBuyPrice(), newSellPrice);

            recalculateGridLineSellAmounts(gridLine, newSellPrice);

        } else {
            log.info("[RECALC-SKIP] 网格 level={}, state={}, 不更新价格", gridLine.getLevel(), state);
        }
    }

    /**
     * 重新计算网格的金额和数量
     * <p>
     * 修复：未成交网格应该保持策略创建时的固定买入数量，不应该因为买入价变化而改变数量
     * 只有已成交网格的买入价变化才需要重新计算金额
     */
    private void recalculateGridLineAmounts(GridLine gridLine, BigDecimal newBuyPrice, BigDecimal newSellPrice) {
        // 关键修复：未成交网格保持策略创建时的固定买入数量
        // 只有已成交网格的买入数量是用户实际买入的，需要保留
        BigDecimal buyQuantity = gridLine.getBuyQuantity();
        if (gridLine.getState() == GridLineState.WAIT_BUY) {
            // 未成交：保持策略创建时的固定数量不变
            // 只需要根据新买入价重新计算金额即可
            buyQuantity = gridLine.getStrategy().getQuantityPerGrid();
        }
        // 如果是已成交，保持用户实际录入的买入数量不变

        BigDecimal buyAmount = buyQuantity.multiply(newBuyPrice).setScale(2, RoundingMode.DOWN);
        gridLine.setBuyQuantity(buyQuantity);
        gridLine.setBuyAmount(buyAmount);

        BigDecimal sellAmount = buyQuantity.multiply(newSellPrice).setScale(2, RoundingMode.DOWN);
        gridLine.setSellAmount(sellAmount);

        BigDecimal profit = sellAmount.subtract(buyAmount);
        gridLine.setProfit(profit);

        BigDecimal profitRate = profit.divide(buyAmount, 6, RoundingMode.HALF_UP);
        gridLine.setProfitRate(profitRate);
    }

    /**
     * 重新计算已买入网格的卖出相关金额
     */
    private void recalculateGridLineSellAmounts(GridLine gridLine, BigDecimal newSellPrice) {
        BigDecimal buyQuantity = gridLine.getBuyQuantity();
        if (buyQuantity != null) {
            // 买入数量保持用户实际录入的不变，只重新计算卖出金额
            BigDecimal sellAmount = buyQuantity.multiply(newSellPrice).setScale(2, RoundingMode.DOWN);
            gridLine.setSellAmount(sellAmount);

            BigDecimal buyAmount = gridLine.getBuyAmount();
            BigDecimal profit = sellAmount.subtract(buyAmount);
            gridLine.setProfit(profit);

            BigDecimal profitRate = profit.divide(buyAmount, 6, RoundingMode.HALF_UP);
            gridLine.setProfitRate(profitRate);
        }
        // 这个方法只更新卖出金额，买入数量保持不变
        // 所以不需要修改买入数量，逻辑正确
    }
}
