package com.panda.snapledger.service;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for payment screenshots (支付宝/微信/银行).
 * Extracts transaction details from OCR text.
 */
@Component
public class PaymentScreenshotParser {

    // Amount patterns: ¥128.50, ￥99.00, 金额：128.50
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("[¥￥]?\\s*([+-]?\\s*[\\d,]+\\.\\d{2})");
    private static final Pattern AMOUNT_WITH_SIGN = Pattern.compile("([+-])\\s*[¥￥]?\\s*([\\d,]+\\.\\d{2})");

    // Date patterns: 2024-03-15, 2024/03/15, 2024年3月15日
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})日?");
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");

    // Merchant patterns
    private static final Pattern ALIPAY_MERCHANT = Pattern.compile("(?:收款方|付款方|商家)[:：]\\s*(.+)");
    private static final Pattern WECHAT_MERCHANT = Pattern.compile("(?:收款方|付款方|商户)[:：]\\s*(.+)");

    // Platform detection
    private static final Pattern ALIPAY_PATTERN = Pattern.compile("支付宝|alipay", Pattern.CASE_INSENSITIVE);
    private static final Pattern WECHAT_PATTERN = Pattern.compile("微信|wechat", Pattern.CASE_INSENSITIVE);

    @Data
    public static class ParseResult {
        private boolean recognized = false;
        private String platform;      // alipay, wechat, bank
        private BigDecimal amount;
        private String recordType;    // 收入, 支出
        private String merchant;
        private LocalDate date;
        private LocalTime time;
        private String rawText;
        private String errorMessage;
    }

    /**
     * Parse OCR text and extract transaction details.
     * @param ocrText the recognized text from OCR
     * @return parsed result
     */
    public ParseResult parse(String ocrText) {
        ParseResult result = new ParseResult();
        result.setRawText(ocrText);

        if (ocrText == null || ocrText.trim().isEmpty()) {
            result.setErrorMessage("OCR text is empty");
            return result;
        }

        // Normalize text
        String normalized = ocrText.replace('\t', ' ').replaceAll("\\s+", " ");

        // Detect platform
        String platform = detectPlatform(normalized);
        if (platform == null) {
            result.setErrorMessage("Unrecognized payment platform");
            return result;
        }
        result.setPlatform(platform);

        // Extract amount
        BigDecimal amount = extractAmount(normalized);
        if (amount == null) {
            result.setErrorMessage("Could not extract amount");
            return result;
        }
        result.setAmount(amount);

        // Determine income/expense
        result.setRecordType(determineRecordType(normalized, amount));

        // Extract merchant
        result.setMerchant(extractMerchant(normalized, platform));

        // Extract date and time
        result.setDate(extractDate(normalized));
        result.setTime(extractTime(normalized));

        result.setRecognized(true);
        return result;
    }

    private String detectPlatform(String text) {
        if (ALIPAY_PATTERN.matcher(text).find()) {
            return "alipay";
        }
        if (WECHAT_PATTERN.matcher(text).find()) {
            return "wechat";
        }
        // TODO: Add bank detection
        return null;
    }

    private BigDecimal extractAmount(String text) {
        // First try to find amount with sign (+/-)
        Matcher signMatcher = AMOUNT_WITH_SIGN.matcher(text);
        while (signMatcher.find()) {
            String amountStr = signMatcher.group(2).replace(",", "");
            return new BigDecimal(amountStr);
        }

        // Fall back to any amount pattern
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        while (matcher.find()) {
            String amountStr = matcher.group(1).replace(",", "").trim();
            // Skip amounts that look like dates or times
            if (amountStr.startsWith("20") && amountStr.length() > 8) {
                continue;
            }
            try {
                return new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
                continue;
            }
        }
        return null;
    }

    private String determineRecordType(String text, BigDecimal amount) {
        String lower = text.toLowerCase();

        // Check for expense indicators first (more specific patterns)
        if (lower.contains("付款成功") || lower.contains("支付成功") || lower.contains("付款方式")
                || lower.contains("消费") || lower.contains("转出") || lower.contains("购买")) {
            return "支出";
        }

        // Check for income indicators
        if (lower.contains("收款到账") || lower.contains("到账") || lower.contains("收入")
                || lower.contains("转入") || lower.contains("退款")) {
            return "收入";
        }

        // Default based on amount sign (already stripped in extraction)
        return "支出";
    }

    private String extractMerchant(String text, String platform) {
        Pattern merchantPattern = "alipay".equals(platform) ? ALIPAY_MERCHANT : WECHAT_MERCHANT;
        Matcher matcher = merchantPattern.matcher(text);
        if (matcher.find()) {
            String merchant = matcher.group(1).trim();
            // Clean up common suffixes
            merchant = merchant.split("\\s+")[0];
            return merchant;
        }
        return null;
    }

    private LocalDate extractDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            try {
                return LocalDate.of(year, month, day);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private LocalTime extractTime(String text) {
        Matcher matcher = TIME_PATTERN.matcher(text);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = Integer.parseInt(matcher.group(2));
            int second = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            try {
                return LocalTime.of(hour, minute, second);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
