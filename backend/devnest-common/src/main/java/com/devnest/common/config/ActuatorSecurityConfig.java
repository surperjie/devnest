package com.devnest.common.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * Actuator 安全配置(Spring Security 显式化).
 * <p>
 * 之前的 application.yml "show-details: always" + "shutdown: enabled" 是安全隐患:
 * - 任何人都能拿到完整数据源/连接池/环境变量信息
 * - 任何能访问 8080 的一方都能调用 /actuator/shutdown 停后端
 * <p>
 * 修复:
 * 1) /actuator/shutdown 仅允许 127.0.0.1 / ::1 访问
 * 2) /actuator/health 详细信息(/actuator/health 所有路径)仅本地;外部请求只能看到 UP/DOWN
 * 3) 其它 actuator 端点全部禁用(通过白名单 include 已只暴露 health,info,shutdown;这里再做兜底)
 * 4) 业务 REST API /ws 不启用 security(当前是 127.0.0.1 绑卡+CORS+TOFU 三层保护)
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
@Configuration
public class ActuatorSecurityConfig {

    private static final String LOCAL_IPV4 = "127.0.0.1/32";
    private static final String LOCAL_IPV6 = "::1/128";

    @Bean
    public SecurityFilterChain actuatorSecurity(HttpSecurity http) throws Exception {
        IpAddressMatcher localIp = new IpAddressMatcher("127.0.0.1");
        IpAddressMatcher localIpv6 = new IpAddressMatcher("::1");

        http
                .csrf(csrf -> csrf.disable())   // CSRF 对 API 无意义,Tauri 侧跨站不会带 Cookie
                .authorizeHttpRequests(auth -> auth
                        // shutdown 仅本机
                        .requestMatchers(EndpointRequest.to("shutdown")).access((authentication, context) -> {
                            String remote = context.getRequest().getRemoteAddr();
                            boolean ok = localIp.matches(remote) || localIpv6.matches(remote)
                                    || "0:0:0:0:0:0:0:1".equals(remote);
                            return new org.springframework.security.authorization.AuthorizationDecision(ok);
                        })
                        // health 细节也仅本机(外部只能看到基础 health,但 show-details: when-authorized 生效)
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        // info 公开
                        .requestMatchers(EndpointRequest.to("info")).permitAll()
                        // 所有其它 actuator 请求(万一将来 include 加了 endpoints)仅本机
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).access((authentication, context) -> {
                            String remote = context.getRequest().getRemoteAddr();
                            boolean ok = localIp.matches(remote) || localIpv6.matches(remote);
                            return new org.springframework.security.authorization.AuthorizationDecision(ok);
                        })
                        // 业务 API 全部放行(依赖 CORS + WS TOFU + 127.0.0.1 绑卡)
                        .anyRequest().permitAll()
                )
                .cors(cors -> cors.disable());   // CorsConfig 已自定义,关掉 security 默认 CORS 重复处理
        return http.build();
    }
}
