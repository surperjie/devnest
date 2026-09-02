package com.devnest.datasource.service;

import com.devnest.datasource.dto.MultiSqlResult;
import com.devnest.datasource.dto.SchemaNode;
import com.devnest.datasource.dto.SqlLogDto;
import com.devnest.datasource.dto.TableDataResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 数据库查询服务:Schema 元数据 + 数据预览 + SQL 执行 + 日志.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
public interface DatabaseQueryService {

    /** 获取库表结构树 */
    List<SchemaNode> getSchemaTree(Long datasourceId);

    /** 分页预览表数据(可指定库名前缀) */
    TableDataResult previewTable(Long datasourceId, String database, String tableName, int page, int size);

    /** 执行 SQL(支持多语句,经黑名单校验,返回多个结果集) */
    MultiSqlResult executeSql(Long datasourceId, String sql, int maxRows);

    /** 获取 SQL 执行历史 */
    Page<SqlLogDto> getSqlHistory(Long datasourceId, Pageable pageable);

    /** 获取最近 SQL(快捷复用) */
    List<SqlLogDto> getRecentSql(Long datasourceId);
}
