package com.panda.snapledger.controller.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * OCR recognition result DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResultDTO {

    private boolean success;
    private String message;

    // Recognized fields
    private String platform;      // alipay, wechat, bank
    private BigDecimal amount;
    private String recordType;    // 收入, 支出
    private String merchant;
    private LocalDate date;
    private LocalTime time;

    // Raw OCR text for debugging
    private String rawText;

    // Pre-filled record data for confirmation
    private RecordDTO record;

    public static OcrResultDTO success(String platform, BigDecimal amount, String recordType,
                                        String merchant, LocalDate date, LocalTime time, String rawText) {
        return OcrResultDTO.builder()
                .success(true)
                .platform(platform)
                .amount(amount)
                .recordType(recordType)
                .merchant(merchant)
                .date(date)
                .time(time)
                .rawText(rawText)
                .build();
    }

    public static OcrResultDTO error(String message) {
        return OcrResultDTO.builder()
                .success(false)
                .message(message)
                .build();
    }
}
