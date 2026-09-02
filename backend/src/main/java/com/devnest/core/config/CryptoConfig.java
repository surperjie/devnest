package com.devnest.core.config;

import com.devnest.common.crypto.CryptoService;
import com.devnest.common.crypto.KeyManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 加密服务 Bean 注册(KeyManager/CryptoService 保持 POJO,便于测试).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@Configuration
public class CryptoConfig {

    @Bean
    public KeyManager keyManager() {
        return new KeyManager();
    }

    @Bean
    public CryptoService cryptoService(KeyManager keyManager) {
        return new CryptoService(keyManager);
    }
}
