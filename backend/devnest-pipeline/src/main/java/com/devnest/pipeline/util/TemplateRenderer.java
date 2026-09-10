package com.devnest.pipeline.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上下文模板渲染:把 {{key}} / {{ key }} 替换为上下文中的值,未定义变量替换为空串.
 * 运行入参、上一步解析出的 KEY=VALUE、内置 run.xxx 均在同一上下文中.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public final class TemplateRenderer {

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([^{}\\s]+)\\s*}}");

    private TemplateRenderer() {
    }

    public static String render(String template, Map<String, String> ctx) {
        if (template == null || template.isEmpty()) {
            return template == null ? "" : template;
        }
        Matcher m = TOKEN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = ctx.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
