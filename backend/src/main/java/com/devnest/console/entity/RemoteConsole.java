package com.devnest.console.entity;

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
 * SSH 远程控制台会话配置实体.
 * 支持直连模式(bastionId=null)和隧道模式(bastionId 非空,经一期跳板连内网主机).
 * 密码字段 AES 加密存储,运行时解密使用,不入库不入日志.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
@Entity
@Table(name = "remote_console_session")
@Getter
@Setter
public class RemoteConsole extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    private String name;

    /** 绑定的跳板ID,NULL=直连模式,非NULL=经该跳板隧道连接 */
    @Column(name = "bastion_id")
    private Long bastionId;

    @Column(name = "remote_host", nullable = false, length = 128)
    private String remoteHost;

    @Column(name = "remote_port", nullable = false)
    private Integer remotePort = 22;

    @Column(name = "ssh_user", nullable = false, length = 64)
    private String sshUser;

    /** AES 加密密文(base64),禁止明文存储 */
    @Column(name = "ssh_password", nullable = false, length = 512)
    private String sshPasswordCipher;

    @Column(name = "remark", length = 255)
    private String remark;

    /** 快捷命令 JSON 数组,如 [{"name":"查日志","command":"tail -f x.log"}],可为空 */
    @Column(name = "quick_commands", columnDefinition = "TEXT")
    private String quickCommands;

    /**
     * 解密返回明文密码(仅运行时使用,不入库不入日志).
     */
    @Transient
    public String decryptPassword(CryptoService crypto) {
        return crypto.decrypt(sshPasswordCipher);
    }
}
