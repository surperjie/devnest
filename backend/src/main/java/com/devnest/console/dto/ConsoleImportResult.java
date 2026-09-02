package com.devnest.console.dto;

import java.util.List;

/**
 * 控制台配置导入结果.successCount 成功数,skipCount 重名跳过数,skippedNames 跳过的名称.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 19:30
 */
public record ConsoleImportResult(
        int successCount,
        int skipCount,
        List<String> skippedNames
) {
}
