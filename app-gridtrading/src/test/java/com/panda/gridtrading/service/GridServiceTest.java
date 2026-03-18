package com.panda.gridtrading.service;

import com.panda.gridtrading.domain.GridLine;
import com.panda.gridtrading.domain.GridLineState;
import com.panda.gridtrading.domain.GridType;
import com.panda.gridtrading.domain.Strategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GridServiceTest {

    public GridServiceTest() {
    }

    @Test
    void manualExecute_shouldKeepFixedQuantityForUnmatchedGridsAfterRecalculation() {
        // Given: 创建策略，basePrice 1.083，quantityPerGrid 700
        // 用户手动执行买入第2网，价格 1.028，数量 1000
        // 期望：
        // - 当前第2网买入数量已经是 1000（用户实际录入）
        // - 第3网及后续未成交网格保持 700 不变
        Long strategyId = 1L;
        Strategy mockStrategy = new Strategy();
        mockStrategy.setId(strategyId);
        mockStrategy.setBasePrice(new BigDecimal("1.083"));
        mockStrategy.setQuantityPerGrid(new BigDecimal("700"));
        mockStrategy.setAmountPerGrid(new BigDecimal("1.083").multiply(new BigDecimal("700")));

        // 创建初始网格线（只创建前3网简化测试）
        List<GridLine> gridLines = new ArrayList<>();

        // 第1网 - SMALL，已买入
        GridLine grid1 = new GridLine();
        grid1.setId(1L);
        grid1.setLevel(1);
        grid1.setGridType(GridType.SMALL);
        grid1.setBuyPrice(new BigDecimal("1.083"));
        grid1.setBuyQuantity(new BigDecimal("700"));
        grid1.setBuyAmount(new BigDecimal("1.083").multiply(new BigDecimal("700")));
        grid1.setState(GridLineState.BOUGHT);
        grid1.setStrategy(mockStrategy);
        gridLines.add(grid1);

        // 第2网 - SMALL，已经买入，用户录入实际数量 1000
        GridLine grid2 = new GridLine();
        grid2.setId(2L);
        grid2.setLevel(2);
        grid2.setGridType(GridType.SMALL);
        grid2.setBuyPrice(new BigDecimal("1.028")); // 用户实际买入价
        grid2.setActualBuyPrice(new BigDecimal("1.028"));
        grid2.setBuyQuantity(new BigDecimal("1000")); // 用户实际录入数量
        grid2.setBuyAmount(new BigDecimal("1.028").multiply(new BigDecimal("1000")));
        grid2.setState(GridLineState.BOUGHT);
        grid2.setStrategy(mockStrategy);
        gridLines.add(grid2);

        // 第3网 - SMALL，等待买入
        GridLine grid3 = new GridLine();
        grid3.setId(3L);
        grid3.setLevel(3);
        grid3.setGridType(GridType.SMALL);
        grid3.setBuyPrice(new BigDecimal("0.9774")); // 1.02885 * 0.95 ≈ 0.9774
        grid3.setBuyQuantity(new BigDecimal("700"));
        grid3.setBuyAmount(new BigDecimal("0.9774").multiply(new BigDecimal("700")));
        grid3.setState(GridLineState.WAIT_BUY);
        grid3.setStrategy(mockStrategy);
        gridLines.add(grid3);

        mockStrategy.setGridLines(gridLines);

        // When: 执行买入后重新计算后续网格
        GridService gridService = new GridService();
        gridService.recalculateSubsequentGrids(mockStrategy, grid2, new BigDecimal("1.028"));

        // Then:
        // 第2网（当前买入）保持用户传入的数量 1000 不变
        assertEquals(new BigDecimal("1000"), grid2.getBuyQuantity());
        // 第3网未成交，应该保持默认 700 不变
        // bug修复前：这里会从原有 buyAmount 反算 → 700 * 1.02885 / 0.9774 ≈ 736
        // bug修复后：直接使用 strategy.quantityPerGrid → 永远 700
        assertEquals(new BigDecimal("700"), grid3.getBuyQuantity());
        // 验证第3网买入金额也跟着正确计算了 = 700 * (1.028 * 0.95) = 700 * 0.9766 ≈ 700 * 0.976 = 683.20
        // 这里 buyPrice = 1.028 × 0.95 = 0.9766 → 保留三位小数向下取整 = 0.976
        BigDecimal expectedAmount = new BigDecimal("700").multiply(new BigDecimal("0.976")).setScale(2, java.math.RoundingMode.DOWN);
        assertEquals(expectedAmount, grid3.getBuyAmount());
        // 最重要的验证：数量保持 700 不变 ✓
        // bug修复前：这里数量会变成 ~736
        // bug修复后：永远保持策略默认的 700
    }
}
