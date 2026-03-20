package com.panda.common.ocr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class BaiduOcrClientTest {

    private BaiduOcrClient client;

    @BeforeEach
    void setUp() {
        // Create client with empty credentials (should not throw)
        client = new BaiduOcrClient("", "", "");
    }

    @Test
    void testCreateWithEmptyCredentials() {
        assertNotNull(client);
    }

    @Test
    void testRecognizeWithoutCredentialsThrows() {
        assertThrows(IllegalStateException.class, () -> {
            client.recognize(null);
        });
    }
}
