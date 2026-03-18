package com.panda.gridtrading.service.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

class ApkFolderGeneratorServiceTest {

    private static final int MIN_SEARCH_VOLUME = 300;

    @Test
    @DisplayName("范围格式 '100-300' 应该取下限值100，低于300应该被过滤")
    void shouldParseRangeFormatAndFilterLowVolume() {
        Integer volume = parseInteger("100-300");
        System.out.println("'100-300' parsed to: " + volume);

        // 期望：范围格式取下限值 100，低于 300，应该被过滤
        assertEquals(100, volume, "范围格式应该取下限值");
        assertFalse(volume >= MIN_SEARCH_VOLUME, "100 应该被过滤");
    }

    @Test
    @DisplayName("范围格式 '300-500' 应该取下限值300，等于300应该保留")
    void shouldParseRangeFormatAndKeepBoundary() {
        Integer volume = parseInteger("300-500");
        System.out.println("'300-500' parsed to: " + volume);

        assertEquals(300, volume, "范围格式应该取下限值");
        assertTrue(volume >= MIN_SEARCH_VOLUME, "300 应该保留");
    }

    @Test
    @DisplayName("范围格式 '50-100' 应该取下限值50，低于300应该被过滤")
    void shouldParseRangeFormatLowVolume() {
        Integer volume = parseInteger("50-100");
        System.out.println("'50-100' parsed to: " + volume);

        assertEquals(50, volume, "范围格式应该取下限值");
        assertFalse(volume >= MIN_SEARCH_VOLUME, "50 应该被过滤");
    }

    @Test
    @DisplayName("范围格式 '500-1000' 应该取下限值500，高于300应该保留")
    void shouldParseRangeFormatHighVolume() {
        Integer volume = parseInteger("500-1000");
        System.out.println("'500-1000' parsed to: " + volume);

        assertEquals(500, volume, "范围格式应该取下限值");
        assertTrue(volume >= MIN_SEARCH_VOLUME, "500 应该保留");
    }

    @Test
    @DisplayName("纯数字格式应该正常解析")
    void shouldParsePureNumber() {
        assertEquals(100, parseInteger("100"));
        assertEquals(300, parseInteger("300"));
        assertEquals(500, parseInteger("500"));
        assertEquals(1000, parseInteger("1,000"));
    }

    @Test
    @DisplayName("带加号格式应该正常解析")
    void shouldParseWithPlus() {
        assertEquals(100, parseInteger("100+"));
        assertEquals(5000, parseInteger("5000+"));
    }

    @Test
    @DisplayName("null和空字符串应该返回null")
    void shouldReturnNullForNullOrEmpty() {
        assertNull(parseInteger(null));
        assertNull(parseInteger(""));
        assertNull(parseInteger("   "));
    }

    /**
     * 修复后的 parseInteger 方法
     * 支持格式：
     * - 纯数字: "100" -> 100
     * - 带逗号: "1,000" -> 1000
     * - 带加号: "100+" -> 100
     * - 范围格式: "100-300" -> 100 (取下限)
     */
    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String trimmed = value.trim();

            // 处理范围格式，如 "100-300"，取下限值
            if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-");
                if (parts.length >= 1) {
                    String lowerBound = parts[0].replaceAll("[^0-9]", "");
                    if (!lowerBound.isEmpty()) {
                        return Integer.parseInt(lowerBound);
                    }
                }
            }

            // 其他格式：去掉非数字字符
            String cleanValue = trimmed.replaceAll("[^0-9]", "");
            if (cleanValue.isEmpty()) {
                return null;
            }
            return Integer.parseInt(cleanValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
