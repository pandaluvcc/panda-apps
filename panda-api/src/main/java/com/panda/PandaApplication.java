package com.panda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Panda应用启动入口
 * 扫描所有业务模块的组件
 */
@SpringBootApplication(scanBasePackages = {
        "com.panda.common",
        "com.panda.gridtrading",
        "com.panda.snapledger"
})
public class PandaApplication {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Starting PandaApplication - DEPLOY CHECK");
        System.out.println("Build timestamp: " + System.currentTimeMillis());
        System.out.println("========================================");
        SpringApplication.run(PandaApplication.class, args);
    }

}
