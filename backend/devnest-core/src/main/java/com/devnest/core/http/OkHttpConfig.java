package com.devnest.core.http;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 统一 OkHttpClient 单例配置.
 * 全项目(HTTP 调试、AI-SQL、AI-Redis 生成、外部 API 调用)共用一份连接池.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
@Configuration
public class OkHttpConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        Dispatcher dispatcher = new Dispatcher();
        // 最大并发 20,单 host 并发 10,匹配一般自用场景和小并发展示
        dispatcher.setMaxRequests(20);
        dispatcher.setMaxRequestsPerHost(10);

        ConnectionPool pool = new ConnectionPool(5, 5, TimeUnit.MINUTES);

        return new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(pool)
                .connectTimeout(3, TimeUnit.SECONDS)
                // AI 大模型流式响应需要更长读超时,这里 60s,调用方可通过 newBuilder().readTimeout(x) 覆盖
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }
}
