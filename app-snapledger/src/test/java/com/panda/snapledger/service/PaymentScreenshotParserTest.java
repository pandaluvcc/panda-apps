package com.panda.snapledger.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

class PaymentScreenshotParserTest {

    private final PaymentScreenshotParser parser = new PaymentScreenshotParser();

    @Test
    void testParseAlipayPayment() {
        String ocrText = "支付宝\n付款成功\n¥ 128.50\n收款方：星巴克咖啡\n2024-03-15 14:30:25\n付款方式：花呗";

        PaymentScreenshotParser.ParseResult result = parser.parse(ocrText);

        assertTrue(result.isRecognized());
        assertEquals("alipay", result.getPlatform());
        assertEquals(new BigDecimal("128.50"), result.getAmount());
        assertEquals("支出", result.getRecordType());
        assertEquals("星巴克咖啡", result.getMerchant());
        assertEquals("2024-03-15", result.getDate().toString());
        assertTrue(result.getTime().toString().startsWith("14:30"));
    }

    @Test
    void testParseWechatPayment() {
        String ocrText = "微信支付\n支付成功\n金额：¥99.00\n收款方：美团外卖\n交易时间：2024-03-16 12:00:00\n支付方式：零钱";

        PaymentScreenshotParser.ParseResult result = parser.parse(ocrText);

        assertTrue(result.isRecognized());
        assertEquals("wechat", result.getPlatform());
        assertEquals(new BigDecimal("99.00"), result.getAmount());
        assertEquals("支出", result.getRecordType());
        assertEquals("美团外卖", result.getMerchant());
    }

    @Test
    void testParseAlipayIncome() {
        String ocrText = "支付宝\n收款到账\n+¥ 500.00\n付款方：张三\n2024-03-17 09:15:00";

        PaymentScreenshotParser.ParseResult result = parser.parse(ocrText);

        assertTrue(result.isRecognized());
        assertEquals("alipay", result.getPlatform());
        assertEquals(new BigDecimal("500.00"), result.getAmount());
        assertEquals("收入", result.getRecordType());
        assertEquals("张三", result.getMerchant());
    }

    @Test
    void testParseUnrecognized() {
        String ocrText = "这是一段普通的文字，没有任何支付信息";

        PaymentScreenshotParser.ParseResult result = parser.parse(ocrText);

        assertFalse(result.isRecognized());
    }

    @Test
    void testParseEmptyText() {
        PaymentScreenshotParser.ParseResult result = parser.parse("");

        assertFalse(result.isRecognized());
    }

    @Test
    void testParseNullText() {
        PaymentScreenshotParser.ParseResult result = parser.parse(null);

        assertFalse(result.isRecognized());
    }
}
