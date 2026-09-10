package com.devnest.datasource.entity;

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
 * 数据库数据源配置实体.
 * 支持 MySQL / 达梦DM,可绑定 SSH 隧道实现内网穿透.
 * 密码字段 AES 加密存储.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Entity
@Table(name = "data_source_config")
@Getter
@Setter
public class DataSourceConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    private String name;

    /** MYSQL / DM */
    @Column(name = "db_type", nullable = false, length = 16)
    private String dbType;

    @Column(name = "host", nullable = false, length = 128)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port = 3306;

    @Column(name = "database_name", length = 128)
    private String databaseName;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    /** AES 加密密文(base64) */
    @Column(name = "password_cipher", nullable = false, length = 512)
    private String passwordCipher;

    /** 绑定的 SSH 跳板 ID,null=直连 */
    @Column(name = "tunnel_bastion_id")
    private Long tunnelBastionId;

    @Column(name = "remark", length = 255)
    private String remark;

    /**
     * 归属用户:单机形态为本机用户名,多人共用时用于数据源访问控制.
     */
    @Column(name = "owner", length = 64)
    private String owner;

    /**
     * 可见性:PRIVATE=仅归属者可见,PUBLIC=团队可见.
     * 当前单机形态默认 PUBLIC(不改变现有行为),为多人共用预留.
     */
    @Column(name = "visibility", length = 16)
    private String visibility = "PUBLIC";

    @Transient
    public String decryptPassword(CryptoService crypto) {
        return crypto.decrypt(passwordCipher);
    }
}
