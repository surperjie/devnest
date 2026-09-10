package com.devnest.pipeline.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * pipeline 模块内部 JSON 序列化工具(基于 core 统一 ObjectMapper 不方便静态引用,这里直接 new).
 * 仅用于入参/args 等小对象,不涉及业务大文本.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public final class PipelineJson {

    private static final Logger log = LoggerFactory.getLogger(PipelineJson.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PipelineJson() {
    }

    public static Map<String, String> toStrMap(Object value) {
        try {
            return MAPPER.convertValue(value, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (IllegalArgumentException e) {
            return new LinkedHashMap<>();
        }
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    public static Map<String, String> parseStrMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("JSON 反序列化(Map)失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public static List<String> parseStrList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("JSON 反序列化(List)失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
