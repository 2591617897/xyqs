package com.campus.post.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对外的一条后台公告。
 *
 * <p>只暴露展示需要的字段：发布人是谁、内部审核状态这些一律不外泄。
 * type 语义：1系统公告 2运营活动 3违规警示；level 语义：1普通 2重要 3紧急。
 */
@Data
public class NoticeVO {

    private Long id;

    private String title;

    /** 正文，可能较长，列表页建议截取前若干字 */
    private String content;

    /** 1系统公告 2运营活动 3违规警示 */
    private Integer type;

    /** 1普通 2重要 3紧急；列表按它倒序，紧急排最前 */
    private Integer level;

    private LocalDateTime publishAt;
}
