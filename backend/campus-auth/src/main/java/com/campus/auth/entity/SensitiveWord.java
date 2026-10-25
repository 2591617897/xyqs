package com.campus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词（campus-auth 侧的只读副本）。
 *
 * <p>这张表的主人是 campus-admin（运营在后台维护），这里只 SELECT 不写，
 * 与 campus-user 持有 t_report_ticket 副本、campus-admin 用 FallbackWriteMapper
 * 直写业务表是同一套「共享库 + 各自持有实体，避免跨模块依赖」的做法。
 *
 * <p>刻意不放 Common 模块：只有内容安全链路需要它，放进 common 会让
 * 所有模块平白无故多一个 Mapper 和一个启动依赖。
 */
@Data
@TableName("t_sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String word;

    /** 1提示（仅记录） 2替换 3禁止发布 */
    private Integer level;

    /** 替换目标，level=2 时生效 */
    private String replaceTo;

    private String category;

    /** 1启用 0停用 */
    private Integer status;

    private Integer hitCount;
}
