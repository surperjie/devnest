package com.devnest.console.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 控制台配置导出载体.version 用于后续格式演进,导入时校验兼容性.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 19:30
 */
public record ConsoleExportPayload(
        Integer version,
        LocalDateTime exportTime,
        List<ConsoleExportItem> consoles
) {
}
