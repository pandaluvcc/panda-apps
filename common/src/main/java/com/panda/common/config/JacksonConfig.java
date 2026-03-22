package com.panda.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 全局配置
 * <p>
 * 解决问题：
 * 1. Long 类型前端精度丢失（序列化为 String）
 * 2. 日期时间格式
 * 3. null 值处理
 * <p>
 * 注意：循环引用问题应通过 DTO 模式解决，不在全局配置处理
 */
@Configuration
public class JacksonConfig implements org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer {

    @Override
    public void customize(org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder) {
        // ========== Long 精度丢失处理 ==========
        // Long 序列化为 String，避免前端 JavaScript 精度丢失
        // JavaScript Number 最大安全整数是 2^53 - 1 = 9007199254740991
        SimpleModule longModule = new SimpleModule();
        longModule.addSerializer(Long.class, new LongToStringSerializer());
        longModule.addSerializer(Long.TYPE, new LongToStringSerializer());
        builder.modules(longModule);

        // ========== 日期时间处理 ==========
        builder.modules(new JavaTimeModule());
        builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ========== 其他配置 ==========
        // 空对象不报错
        builder.featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        // 忽略未知属性
        builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // null 值不序列化（减少传输数据量）
        builder.serializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Long 转 String 序列化器
     */
    public static class LongToStringSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value != null) {
                gen.writeString(value.toString());
            }
        }
    }
}
