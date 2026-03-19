package com.panda.gridtrading.service.ocr;

/**
 * Re-export from common module for backward compatibility.
 * This class is deprecated, use com.panda.common.ocr.BaiduOcrClient directly.
 */
@Deprecated
@Service
public class BaiduOcrClient extends com.panda.common.ocr.BaiduOcrClient {

    public BaiduOcrClient(
            org.springframework.beans.factory.annotation.Value("${baidu.ocr.app-id:}") String appId,
            org.springframework.beans.factory.annotation.Value("${baidu.ocr.api-key:}") String apiKey,
            org.springframework.beans.factory.annotation.Value("${baidu.ocr.secret-key:}") String secretKey
    ) {
        super(appId, apiKey, secretKey);
    }
}
