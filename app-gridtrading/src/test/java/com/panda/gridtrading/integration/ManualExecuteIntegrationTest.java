package com.panda.gridtrading.integration;

import com.panda.gridtrading.domain.*;
import com.panda.gridtrading.engine.GridEngine;
import com.panda.gridtrading.service.GridService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 完整复现用户实际场景：
 * 1. 导入创建策略，第1网已经买入，basePrice=1.083，quantityPerGrid=700
 * 2. 用户手动执行买入第2网，price=1.028，quantity=1000
 * 3. 期望：
 *    - 第2网 buyQuantity = 1000（用户实际录入）
 *    - 第3网 buyQuantity = 700（保持默认）
 *    - 第3网 buyPrice = 1.028 * 0.95 = 0.9766 → 向下取整 0.976
 */
public class ManualExecuteIntegrationTest {

    @Test
    void fullManualExecuteWorkflow_shouldKeepCorrectQuantities() {
        // Given: 创建策略，完全按照用户实际场景
        Long strategyId = 1L;
        Strategy mockStrategy = new Strategy();
        mockStrategy.setId(strategyId);
        mockStrategy.setBasePrice(new BigDecimal("1.083"));
        mockStrategy.setQuantityPerGrid(new BigDecimal("700"));
        BigDecimal amountPerGrid = new BigDecimal("1.083").multiply(new BigDecimal("700"));
        mockStrategy.setAmountPerGrid(amountPerGrid);

        // 创建完整初始网格（至少前3网）
        List<GridLine> gridLines = new ArrayList<>();

        // 第1网 - SMALL，已买入，数量 700（正确）
        GridLine grid1 = new GridLine();
        grid1.setId(1L);
        grid1.setLevel(1);
        grid1.setGridType(GridType.SMALL);
        grid1.setBuyPrice(new BigDecimal("1.083"));
        grid1.setActualBuyPrice(new BigDecimal("1.083"));
        grid1.setBuyQuantity(new BigDecimal("700"));
        grid1.setBuyAmount(new BigDecimal("1.083").multiply(new BigDecimal("700")).setScale(2, RoundingMode.DOWN));
        grid1.setState(GridLineState.BOUGHT);
        grid1.setBuyCount(1);
        grid1.setStrategy(mockStrategy);
        gridLines.add(grid1);

        // 第2网 - SMALL，初始等待买入，价格 1.083 * 0.95 = 1.02885 → 向下取整 1.028，数量默认 700
        GridLine grid2 = new GridLine();
        grid2.setId(2L);
        grid2.setLevel(2);
        grid2.setGridType(GridType.SMALL);
        grid2.setBuyPrice(new BigDecimal("1.028"));
        grid2.setBuyQuantity(new BigDecimal("700"));
        grid2.setBuyAmount(new BigDecimal("1.028").multiply(new BigDecimal("700")).setScale(2, RoundingMode.DOWN));
        grid2.setState(GridLineState.WAIT_BUY);
        grid2.setStrategy(mockStrategy);
        gridLines.add(grid2);

        // 第3网 - SMALL，初始等待买入，价格 1.028 * 0.95 = 0.9766 → 向下取整 0.976，数量默认 700
        GridLine grid3 = new GridLine();
        grid3.setId(3L);
        grid3.setLevel(3);
        grid3.setGridType(GridType.SMALL);
        grid3.setBuyPrice(new BigDecimal("0.976"));
        grid3.setBuyQuantity(new BigDecimal("700"));
        grid3.setBuyAmount(new BigDecimal("0.976").multiply(new BigDecimal("700")).setScale(2, RoundingMode.DOWN));
        grid3.setState(GridLineState.WAIT_BUY);
        grid3.setStrategy(mockStrategy);
        gridLines.add(grid3);

        mockStrategy.setGridLines(gridLines);

        // When: 模拟完整流程 - 用户手动买入第2网，价格 1.028，数量 1000
        GridEngine gridEngine = new GridEngine(null, null, null, null, new GridService());
        // 先执行买入，这会更新 grid2 的数量并触发重新计算
        // 使用反射调用 executeBuy 因为它是 private
        try {
            var method = GridEngine.class.getDeclaredMethod(
                "executeBuy", Strategy.class, GridLine.class, BigDecimal.class, BigDecimal.class, BigDecimal.class
            );
            method.setAccessible(true);
            BigDecimal amount = new BigDecimal("1.028").multiply(new BigDecimal("1000"));
            method.invoke(gridEngine, mockStrategy, grid2, new BigDecimal("1.028"), new BigDecimal("1000"), amount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Then: 验证结果
        // 1. 第2网必须保持用户实际录入的数量 1000 ✅
        assertEquals(new BigDecimal("1000"), grid2.getBuyQuantity(),
            "第2网买入数量应该保持用户实际录入的 1000");
        assertEquals(new BigDecimal("1028.00").setScale(2), grid2.getBuyAmount().setScale(2),
            "第2网买入金额应该是 1.028 × 1000 = 1028.00");

        // 2. 第3网数量必须保持默认 700 ✅
        assertEquals(new BigDecimal("700"), grid3.getBuyQuantity(),
            "第3网未成交，必须保持策略默认数量 700");

        // 3. 第3网买入价应该是 1.028 × 0.95 = 0.9766 → 向下取整 = 0.976 ✅
        assertEquals(new BigDecimal("0.976"), grid3.getBuyPrice(),
            "第3网买入价计算错误");

        // 4. 第3网买入金额应该是 0.976 × 700 = 683.20 ✅
        BigDecimal expectedAmount = new BigDecimal("0.976").multiply(new BigDecimal("700")).setScale(2, RoundingMode.DOWN);
        assertEquals(expectedAmount, grid3.getBuyAmount(),
            "第3网买入金额计算错误");

        System.out.println("=== 验证通过 ===");
        System.out.println("第2网 buyQuantity: " + grid2.getBuyQuantity());
        System.out.println("第2网 buyAmount: " + grid2.getBuyAmount());
        System.out.println("第3网 buyQuantity: " + grid3.getBuyQuantity());
        System.out.println("第3网 buyPrice: " + grid3.getBuyPrice());
        System.out.println("第3网 buyAmount: " + grid3.getBuyAmount());
    }
}
