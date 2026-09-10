package com.devnest.common.context;

import org.springframework.stereotype.Component;

/**
 * 单机实现:以本机登录用户名作为"操作者"标识.
 * <p>
 * 后端仅绑定回环地址、单用户使用时,本机用户名即是唯一操作者.
 * 多人共用形态请替换为基于登录态的实现.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/10
 */
@Component
public class LocalUserProvider implements CurrentUserProvider {

    private static final String FALLBACK = "local";

    @Override
    public String currentUser() {
        String user = System.getProperty("user.name");
        return (user == null || user.isBlank()) ? FALLBACK : user;
    }
}
