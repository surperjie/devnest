package com.devnest.common.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * CORS 安全配置.
 * <p>
 * 1) allowedOriginPatterns 精确白名单(前端开发端口 1420 + Tauri 协议),默认不允许任何未列的来源.
 * 2) 额外 Filter 兜底:若请求带 Origin 头且不在白名单,响应里禁止浏览器读取(返回 403 CORS_ORIGIN_BLOCKED).
 *    这能拦截 WebMvc CorsRegistry 在 Spring 版本差异或 mapping 未覆盖时的静默放行.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    static final List<String> ALLOWED_ORIGINS = Arrays.asList(
            "http://127.0.0.1:1420",
            "http://localhost:1420",
            "tauri://localhost",
            "http://tauri.localhost"
    );

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(ALLOWED_ORIGINS.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With", "Accept", "Origin")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
        registry.addMapping("/ws/**")
                .allowedOriginPatterns(ALLOWED_ORIGINS.toArray(new String[0]))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 兜底 Filter:拒绝任何携带非白名单 Origin 的请求(含 API/WS/Actuator).
     * 放在 ApplicationFilterChain 最早阶段执行,不依赖 CorsRegistry 是否覆盖到具体路径.
     */
    @Bean
    public Filter corsOriginBlockFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String origin = request.getHeader("Origin");
                if (origin != null && !origin.isEmpty() && !ALLOWED_ORIGINS.contains(origin)) {
                    log.warn("拦截非法 Origin 请求: method={} uri={} origin={}",
                            request.getMethod(), request.getRequestURI(), origin);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"code\":4030,\"msg\":\"CORS_ORIGIN_BLOCKED\",\"success\":false}");
                    return;
                }
                // 同源/不声明 Origin 的请求,交给下游鉴权
                chain.doFilter(request, response);
            }
        };
    }
}
