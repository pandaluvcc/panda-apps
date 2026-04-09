package com.panda.snapledger.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 账户分组字典初始化器
 * <p>
 * 每次应用启动后执行 db/seed/account-group-defaults.sql，
 * 使用 INSERT IGNORE 保证幂等——数据已存在则跳过，不存在则补充。
 * 同时兼容 H2（dev）和 MySQL（prod）。
 */
@Component
public class AccountGroupSeeder {

    private static final Logger log = LoggerFactory.getLogger(AccountGroupSeeder.class);
    private static final String SEED_FILE = "db/seed/account-group-defaults.sql";

    private final JdbcTemplate jdbcTemplate;

    public AccountGroupSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            ClassPathResource resource = new ClassPathResource(SEED_FILE);
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            jdbcTemplate.execute(sql);
            log.info("账户分组字典初始化完成");
        } catch (IOException e) {
            log.error("账户分组种子文件读取失败: {}", SEED_FILE, e);
        } catch (Exception e) {
            log.error("账户分组字典初始化失败", e);
        }
    }
}
