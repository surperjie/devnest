package com.devnest.common.crypto;

import jakarta.annotation.PostConstruct;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

/**
 * 主密钥管理:首次启动生成随机密钥存本地文件,运行时加载到内存.
 * 简化版:随机密钥 + 文件存储. TODO 后续可扩展机器 ID 派生加密.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public class KeyManager {

    private static final Path KEY_FILE =
            Paths.get(System.getProperty("user.home"), ".devnest", "master.key");
    private static final int KEY_LEN = 32; // 256 bit

    private volatile byte[] masterKey;

    @PostConstruct
    void init() throws Exception {
        this.masterKey = loadOrGenerate();
    }

    /**
     * 获取主密钥(内存级,程序退出即失效).
     */
    public byte[] getMasterKey() {
        return masterKey;
    }

    private byte[] loadOrGenerate() throws Exception {
        if (Files.exists(KEY_FILE)) {
            byte[] loaded = Files.readAllBytes(KEY_FILE);
            if (loaded.length == KEY_LEN) {
                return loaded;
            }
        }
        byte[] key = new byte[KEY_LEN];
        SecureRandom.getInstanceStrong().nextBytes(key);
        Files.createDirectories(KEY_FILE.getParent());
        Files.write(KEY_FILE, key);
        return key;
    }
}
