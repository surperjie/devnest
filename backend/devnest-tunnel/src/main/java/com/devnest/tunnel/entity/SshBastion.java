package com.devnest.tunnel.entity;

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
 * SSH 跳板配置实体.密码字段持久化 AES 密文,出库后调用解密访问.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@Entity
@Table(name = "ssh_bastion")
@Getter
@Setter
public class SshBastion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    private String name;

    @Column(name = "ssh_host", nullable = false, length = 128)
    private String sshHost;

    @Column(name = "ssh_port", nullable = false)
    private Integer sshPort = 22;

    @Column(name = "ssh_user", nullable = false, length = 64)
    private String sshUser;

    /** AES 加密密文(base64),禁止明文存储 */
    @Column(name = "ssh_password", nullable = false, length = 512)
    private String sshPasswordCipher;

    @Column(name = "remark", length = 255)
    private String remark;

    /**
     * 解密返回明文密码(仅运行时使用,不入库不入日志).
     */
    @Transient
    public String decryptPassword(CryptoService crypto) {
        return crypto.decrypt(sshPasswordCipher);
    }
}
