package com.devnest.tunnel.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 跳板配置导出载体.version 用于后续格式演进,导入时校验兼容性.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 16:40
 */
public record BastionExportPayload(
        Integer version,
        LocalDateTime exportTime,
        List<BastionExportItem> bastions
) {
}
