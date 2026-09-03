package com.devnest.redis.entity;

import com.devnest.common.crypto.CryptoService;
import com.devnest.core.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * Redis 实例配置.密码字段 AES-256-GCM 加密(存 cipher 文).
 * 可选绑定 SSH 跳板(sshBastionId),此时连接时通过 TunnelPortForwarder SPI 拿到本地端口转发.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 11:00
 */
@Entity
@Table(name = "redis_instance_config")
@Getter
@Setter
public class RedisInstanceConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "host", nullable = false, length = 128)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    /** AES-256-GCM 加密后的密码(null = 无密码) */
    @Column(name = "password_cipher", length = 512)
    private String passwordCipher;

    /** 默认选中的 db 索引 */
    @Column(name = "db_index", nullable = false)
    private Integer dbIndex = 0;

    /** 连接超时(ms) */
    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 2000;

    /** Jedis 池最大连接数 */
    @Column(name = "max_connections", nullable = false)
    private Integer maxConnections = 8;

    /** 可选绑定 SSH 跳板(tunnel SPI 转发到本地端口) */
    @Column(name = "ssh_bastion_id")
    private Long sshBastionId;

    @Column(name = "remark", length = 255)
    private String remark;

    @Transient
    public String decryptPassword(CryptoService crypto) {
        return crypto.decrypt(passwordCipher);
    }
}
