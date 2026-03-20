package com.panda.snapledger.controller;

import com.panda.common.ocr.BaiduOcrClient;
import com.panda.snapledger.controller.dto.OcrConfirmDTO;
import com.panda.snapledger.controller.dto.OcrResultDTO;
import com.panda.snapledger.controller.dto.RecordDTO;
import com.panda.snapledger.service.PaymentScreenshotParser;
import com.panda.snapledger.service.RecordService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * OCR image recognition controller for payment screenshots.
 */
@RestController("snapOcrController")
@RequestMapping("/api/snapledger/ocr")
@CrossOrigin(origins = "*")
public class OcrController {

    private final BaiduOcrClient baiduOcrClient;
    private final PaymentScreenshotParser paymentScreenshotParser;
    private final RecordService recordService;

    public OcrController(BaiduOcrClient baiduOcrClient,
                         PaymentScreenshotParser paymentScreenshotParser,
                         RecordService recordService) {
        this.baiduOcrClient = baiduOcrClient;
        this.paymentScreenshotParser = paymentScreenshotParser;
        this.recordService = recordService;
    }

    /**
     * Upload image and recognize transaction details.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrResultDTO> recognize(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(OcrResultDTO.error("请选择要上传的图片"));
        }

        try {
            // Step 1: OCR recognize
            String ocrText = baiduOcrClient.recognize(file);

            // Step 2: Parse payment screenshot
            PaymentScreenshotParser.ParseResult parseResult = paymentScreenshotParser.parse(ocrText);

            if (!parseResult.isRecognized()) {
                return ResponseEntity.ok(OcrResultDTO.error(
                        parseResult.getErrorMessage() != null
                                ? parseResult.getErrorMessage()
                                : "无法识别支付截图，请确保图片清晰且包含完整的支付信息"
                ));
            }

            // Step 3: Build response with pre-filled record
            OcrResultDTO result = OcrResultDTO.success(
                    parseResult.getPlatform(),
                    parseResult.getAmount(),
                    parseResult.getRecordType(),
                    parseResult.getMerchant(),
                    parseResult.getDate() != null ? parseResult.getDate() : LocalDate.now(),
                    parseResult.getTime() != null ? parseResult.getTime() : LocalTime.now(),
                    ocrText
            );

            // Pre-fill record DTO for frontend
            RecordDTO recordDTO = new RecordDTO();
            recordDTO.setAmount(parseResult.getAmount());
            recordDTO.setRecordType(parseResult.getRecordType());
            recordDTO.setMerchant(parseResult.getMerchant());
            recordDTO.setDate(parseResult.getDate() != null ? parseResult.getDate() : LocalDate.now());
            recordDTO.setTime(parseResult.getTime());
            recordDTO.setAccount(detectAccount(parseResult.getPlatform()));
            recordDTO.setDescription("来自" + getPlatformName(parseResult.getPlatform()) + "截图导入");
            result.setRecord(recordDTO);

            return ResponseEntity.ok(result);

        } catch (IllegalStateException e) {
            // OCR not configured
            return ResponseEntity.ok(OcrResultDTO.error("OCR服务未配置: " + e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.ok(OcrResultDTO.error("图片读取失败: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(OcrResultDTO.error("识别失败: " + e.getMessage()));
        }
    }

    /**
     * Confirm and save the recognized record.
     */
    @PostMapping("/confirm")
    public ResponseEntity<RecordDTO> confirm(@RequestBody OcrConfirmDTO dto) {
        if (dto == null || dto.getAmount() == null) {
            return ResponseEntity.badRequest().build();
        }

        RecordDTO recordDTO = new RecordDTO();
        recordDTO.setAccount(dto.getAccount());
        recordDTO.setMainCategory(dto.getMainCategory());
        recordDTO.setSubCategory(dto.getSubCategory());
        recordDTO.setAmount(dto.getAmount());
        recordDTO.setRecordType(dto.getRecordType());
        recordDTO.setMerchant(dto.getMerchant());
        recordDTO.setDate(dto.getDate() != null ? dto.getDate() : LocalDate.now());
        recordDTO.setTime(dto.getTime());
        recordDTO.setDescription(dto.getDescription());
        recordDTO.setName(dto.getMerchant()); // Use merchant as name

        RecordDTO saved = recordService.create(recordDTO);
        return ResponseEntity.ok(saved);
    }

    private String detectAccount(String platform) {
        if (platform == null) return null;
        return switch (platform) {
            case "alipay" -> "支付宝";
            case "wechat" -> "微信";
            default -> null;
        };
    }

    private String getPlatformName(String platform) {
        if (platform == null) return "未知";
        return switch (platform) {
            case "alipay" -> "支付宝";
            case "wechat" -> "微信";
            default -> platform;
        };
    }
}
