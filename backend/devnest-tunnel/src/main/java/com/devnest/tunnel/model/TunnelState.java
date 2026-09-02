package com.devnest.tunnel.model;

/**
 * SSH 隧道状态机.
 * IDLE → CONNECTING → RUNNING → RECONNECTING → RUNNING / ERROR → CLOSED.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public enum TunnelState {

    /** 未启动 */
    IDLE,
    /** 连接中(首次或重连) */
    CONNECTING,
    /** 正常运行 */
    RUNNING,
    /** 重连中(心跳检测失败后自动重连) */
    RECONNECTING,
    /** 断线/重连失败/端口异常 */
    ERROR,
    /** 手动关闭或程序退出 */
    CLOSED;

    public boolean isRunning() {
        return this == RUNNING;
    }

    public boolean isTerminal() {
        return this == CLOSED || this == ERROR;
    }
}
