package com.devnest.tunnel.dto;

import java.util.List;

/**
 * 跳板配置导入结果.successCount 成功数,skipCount 重名跳过数,skippedNames 跳过的名称.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 16:40
 */
public record BastionImportResult(
        int successCount,
        int skipCount,
        List<String> skippedNames
) {
}
