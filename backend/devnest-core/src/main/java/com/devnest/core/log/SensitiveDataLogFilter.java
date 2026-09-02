package com.devnest.core.log;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感字段日志/响应脱敏配置.
 * <p>
 * 通过注册一个 Jackson Module(内部挂 BeanSerializerModifier) 实现:
 * - 字段名命中白名单集合 (password / cipher / secret / token / apiKey 及常见变体)
 * - 字段值替换为 "***"
 * - String 类型之外的字段不处理(防止把 Integer password 改坏)
 *
 * 注意:代码里手动 log.info("xx={}", passwordStr) 的泄露本 Filter 不能覆盖,
 *      需配合 @ToString.Exclude + 代码审查双保险.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
@Configuration
public class SensitiveDataLogFilter {

    /** 命中即脱敏的字段名(忽略大小写+下划线) */
    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "password", "passwordcipher", "password_cipher",
            "sshpassword", "ssh_password",
            "apikey", "api_key", "apisecret", "api_secret",
            "secret", "token", "access_token", "refreshtoken", "refresh_token",
            "privatekey", "private_key", "privatekeycipher",
            "passphrase", "passphrasecipher", "passphrase_cipher",
            "credential", "credentials"
    ));

    private static final String MASK = "***";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer sensitiveMaskCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("SensitiveMaskModule");
            module.setSerializerModifier(new BeanSerializerModifier() {
                @Override
                public List<BeanPropertyWriter> changeProperties(
                        SerializationConfig config, BeanDescription beanDesc,
                        List<BeanPropertyWriter> beanProperties) {
                    for (BeanPropertyWriter w : beanProperties) {
                        String rawName = w.getName() == null ? "" : w.getName();
                        String key = rawName.toLowerCase().replace("_", "");
                        JavaType type = w.getType();
                        if (type != null && type.getRawClass() == String.class
                                && SENSITIVE_KEYS.contains(key)) {
                            w.assignSerializer(new SensitiveValueSerializer());
                        }
                    }
                    return beanProperties;
                }
            });
            builder.modules(module);
        };
    }

    static class SensitiveValueSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
                throws java.io.IOException {
            gen.writeString(value == null ? null : MASK);
        }
    }
}
