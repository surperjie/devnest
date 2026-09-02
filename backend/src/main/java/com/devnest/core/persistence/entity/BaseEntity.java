package com.devnest.core.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 实体基类:统一 create_time/update_time.
 * id 由子类定义(自增),基类管时间戳(@CreationTimestamp/@UpdateTimestamp 由 Hibernate 自动填充,避免 insert 传 NULL).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Column(name = "create_time", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updateTime;

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
