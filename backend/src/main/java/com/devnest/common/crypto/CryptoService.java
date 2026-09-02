package com.devnest.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 对称加密服务,用于敏感字段(SSH 密码/数据库密码/AI Key)入库加密.
 * 密文格式:base64(IV(12B) || ciphertext || tag(16B)).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public class CryptoService {

    private static final String ALGO = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_LEN_BITS = 128;

    public static final String MASK = "********";

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(KeyManager keyManager) {
        this.key = new SecretKeySpec(keyManager.getMasterKey(), ALGO);
    }

    /**
     * 加密明文为 base64 密文,null 原样返回.
     */
    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /**
     * 解密 base64 密文为明文,null/空原样返回.
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        try {
            byte[] in = Base64.getDecoder().decode(cipherText);
            byte[] iv = Arrays.copyOf(in, IV_LEN);
            byte[] ct = Arrays.copyOfRange(in, IV_LEN, in.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }

    /**
     * 脱敏占位,前端编辑时密码字段显示此值表示未修改.
     */
    public String mask() {
        return MASK;
    }
}
