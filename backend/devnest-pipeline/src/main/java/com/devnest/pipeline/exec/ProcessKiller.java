package com.devnest.pipeline.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 进程树终止工具.
 * Windows 下 cmd/python 可能派生子进程,必须先杀子孙再杀根进程,避免孤儿进程.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public final class ProcessKiller {

    private static final Logger log = LoggerFactory.getLogger(ProcessKiller.class);

    private ProcessKiller() {
    }

    public static void killTree(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.descendants().forEach(ph -> {
                try {
                    ph.destroy();
                } catch (Exception ignored) {
                    // 子进程可能已退出
                }
            });
        } catch (Exception ignored) {
            // descendants() 在某些平台可能不支持
        }
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                try {
                    process.descendants().forEach(ph -> {
                        try {
                            ph.destroyForcibly();
                        } catch (Exception ignored) {
                            // ignore
                        }
                    });
                } catch (Exception ignored) {
                    // ignore
                }
                process.destroyForcibly();
                log.info("进程未在 2s 内退出,已强制结束");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
