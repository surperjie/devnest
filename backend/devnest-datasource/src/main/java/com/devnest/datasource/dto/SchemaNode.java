package com.devnest.datasource.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 库表结构树节点.
 * - type=DATABASE: 数据库名,children=表列表
 * - type=TABLE: 表名,children=字段列表
 * - type=VIEW: 视图名
 * - type=COLUMN: 字段名,extra=类型+主键+注释
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Data
@NoArgsConstructor
public class SchemaNode {
    private String name;
    private String type;
    private String remark;
    private List<SchemaNode> children = new ArrayList<>();
    /** 字段类型(TABLE 子节点用) */
    private String dataType;
    /** 是否主键 */
    private boolean primaryKey;
}
