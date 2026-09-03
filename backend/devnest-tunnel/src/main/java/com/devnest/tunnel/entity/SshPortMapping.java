package com.devnest.tunnel.entity;

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
 * SSH 端口映射模板.一条映射 = 远端 (host:port) → 本地端口.
 * preferred_local_port 为用户期望,allocated_local_port 为运行时实际.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@Entity
@Table(name = "ssh_port_mapping")
@Getter
@Setter
public class SshPortMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bastion_id", nullable = false)
    private Long bastionId;

    @Column(name = "remote_host", nullable = false, length = 128)
    private String remoteHost;

    @Column(name = "remote_port", nullable = false)
    private Integer remotePort;

    /** 用户期望本地端口,可空(空则自动分配) */
    @Column(name = "preferred_local_port")
    private Integer preferredLocalPort;

    /** 运行时实际分配端口(运行中才有值,停止后清空,不入库持久化) */
    @Transient
    private Integer allocatedLocalPort;

    @Column(name = "label", length = 64)
    private String label;

    /**
     * 上次启动状态:1=用户启动(重启服务时应自动恢复),0=停止(启动时不连).
     * 启动成功后置 true,用户 stopTunnel 置 false,心跳/重连达到上限断开也置 false.
     */
    @Column(name = "last_running", nullable = false)
    private Boolean lastRunning = Boolean.FALSE;
}
