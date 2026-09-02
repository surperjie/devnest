package com.devnest.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 配置:允许前端跨域访问后端.
 * 开发期前端 http://127.0.0.1:1420,生产期 Tauri 桌面端 tauri://localhost 或 http://tauri.localhost.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 16:30
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://127.0.0.1:1420",
                        "http://localhost:1420",
                        "tauri://localhost",
                        "http://tauri.localhost"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
