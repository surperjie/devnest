package com.devnest.common.context;

/**
 * 当前操作用户提供者.
 * <p>
 * 单机形态下实现返回本机登录用户;将来后端部署为多人共用时,
 * 只需替换实现(从登录态 / 会话取值),审计与数据源归属等调用方无需改动.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/10
 */
public interface CurrentUserProvider {

    /**
     * 当前操作用户标识,实现须保证不返回 null.
     */
    String currentUser();
}
