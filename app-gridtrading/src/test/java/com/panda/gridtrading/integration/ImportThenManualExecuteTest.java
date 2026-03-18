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
 * 完全复现用户实际场景：
 * 1. OCR导入图片创建策略 → 第1网已经买入，第2网等待买入
 * 2. 用户手动执行买入第2网，输入价格 1.028，数量 1000
 * 3. 验证：
 *    - 第1网：买入数量保持 700 不变
 *    - 第2网：买入数量变成 1000（用户实际录入）
 *    - 第3网：买入价格 = 1.028 × 0.95 = 0.976，买入数量保持 700 不变
 *    - 第4网：买入价格 = 0.976 × 0.95 = 0.927，买入数量保持 700 不变
 */
public class ImportThenManualExecuteTest {

    @Test
    void importCreatedStrategy_thenManualExecuteSecondGrid_shouldKeepCorrectQuantities() {
        // Given: 导入创建策略后，第1网已买入，第2网等待买入，完全按照用户实际数据
        Long strategyId = 1L;
        Strategy mockStrategy = new Strategy();
        mockStrategy.setId(strategyId);
        mockStrategy.setBasePrice(new BigDecimal("1.083"));
        mockStrategy.setQuantityPerGrid(new BigDecimal("700"));
        BigDecimal amountPerGrid = new BigDecimal("1.083").multiply(new BigDecimal("700"));
        mockStrategy.setAmountPerGrid(amountPerGrid);

        List<GridLine> gridLines = new ArrayList<>();

        // 第1网 - 已导入买入，数量 700，正确
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

        // 第2网 - 导入后等待买入，初始数量 700，价格 1.028
        GridLine grid2 = new GridLine();
        grid2.setId(2L);
        grid2.setLevel(2);
        grid2.setGridType(GridType.SMALL);
        grid2.setBuyPrice(new BigDecimal("1.028"));
        // 初始未成交，actualBuyPrice = null
        grid2.setActualBuyPrice(null);
        grid2.setBuyQuantity(new BigDecimal("700"));
        grid2.setBuyAmount(new BigDecimal("1.028").multiply(new BigDecimal("700")).setScale(2, RoundingMode.DOWN));
        grid2.setState(GridLineState.WAIT_BUY);
        grid2.setStrategy(mockStrategy);
        gridLines.add(grid2);

        // 第3网 - 导入后等待买入，初始数量 700，价格 0.976
        GridLine grid3 = new GridLine();
        grid3.setId(3L);
        grid3.setLevel(3);
        grid3.setGridType(GridType.SMALL);
        grid3.setBuyPrice(new BigDecimal("0.976"));
        grid3.setActualBuyPrice(null);
        grid3.setBuyQuantity(new BigDecimal("700"));
        grid3.setBuyAmount(new BigDecimal("0.976").multiply(new BigDecimal("700")).setScale(2, RoundingMode.DOWN));
        grid3.setState(GridLineState.WAIT_BUY);
        grid3.setStrategy(mockStrategy);
        gridLines.add(grid3);

        // 第4网 - 导入后等待买入，初始数量 700，价格 0.927
        GridLine grid4 = new GridLine();
        grid4.setId(4L);
        grid4.setLevel(4);
        grid4.setGridType(GridType.SMALL);
        grid4.setBuyPrice(new BigDecimal("0.927"));
        grid4.setActualBuyPrice(null);
        grid4.setBuyQuantity(new BigDecimal("700"));
        grid4.setBuyAmount(new BigDecimal("0.927").multiply(new BigDecimal("700")).setScale(2, RoundingMode.DOWN));
        grid4.setState(GridLineState.WAIT_BUY);
        grid4.setStrategy(mockStrategy);
        gridLines.add(grid4);

        mockStrategy.setGridLines(gridLines);

        // When: 用户手动买入第2网，价格 1.028，数量 1000
        // executeBuy 会更新 grid2，然后触发 recalculateSubsequentGrids
        GridEngine gridEngine = new GridEngine(null, null, null, null, new GridService());
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
        // 1. 第1网必须保持 700 不变 ✅
        assertEquals(new BigDecimal("700"), grid1.getBuyQuantity(),
            "第1网已买入，必须保持原有数量 700");

        // 2. 第2网必须更新为用户实际录入的 1000 ✅
        assertEquals(new BigDecimal("1000"), grid2.getBuyQuantity(),
            "第2网用户录入 1000，必须变成 1000");
        assertEquals(new BigDecimal("1028.00").setScale(2), grid2.getBuyAmount().setScale(2),
            "第2网买入金额必须是 1.028 × 1000 = 1028.00");

        // 3. 第3网必须保持数量 700 ✅ 价格必须是 1.028 × 0.95 = 0.976 ✅
        assertEquals(new BigDecimal("700"), grid3.getBuyQuantity(),
            "第3网未成交，必须保持策略默认数量 700");
        assertEquals(new BigDecimal("0.976"), grid3.getBuyPrice(),
            "第3网买入价必须是 1.028 × 0.95 = 0.976");
        BigDecimal expectedAmount3 = new BigDecimal("0.976").multiply(new BigDecimal("700")).setScale(2, RoundingMode.DOWN);
        assertEquals(expectedAmount3, grid3.getBuyAmount(),
            "第3网买入金额计算错误");

        // 4. 第4网必须保持数量 700 ✅ 价格必须是 0.976 × 0.95 = 0.927 ✅
        // （这个就是用户日志中看到的，现在应该正确了！）
        assertEquals(new BigDecimal("700"), grid4.getBuyQuantity(),
            "第4网未成交，必须保持策略默认数量 700");
        assertEquals(new BigDecimal("0.927"), grid4.getBuyPrice(),
            "第4网买入价必须是 0.976 × 0.95 = 0.927");

        System.out.println("=== 完整导入场景验证通过 ===");
        System.out.println("第1网 buyQuantity: " + grid1.getBuyQuantity());
        System.out.println("第2网 buyQuantity: " + grid2.getBuyQuantity());
        System.out.println("第3网 buyQuantity: " + grid3.getBuyQuantity() + ", buyPrice: " + grid3.getBuyPrice());
        System.out.println("第4网 buyQuantity: " + grid4.getBuyQuantity() + ", buyPrice: " + grid4.getBuyPrice());
    }
}
