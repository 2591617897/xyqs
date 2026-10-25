package com.campus.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台表白墙列表行。
 *
 * <p><b>realUserId / realNickname 只出现在后台</b>：表白墙前台恒定匿名，
 * 后台要能追溯到真人做违规处置，所以这一行是唯一把两者放一起的地方，
 * 由接口级权限 admin:content:wall:detail 守着。
 */
@Data
public class WallRow {

    private Long id;

    private String content;

    private String images;

    private Long schoolId;

    private String schoolName;

    private String category;

    /** 前台展示的匿名昵称 */
    private String anonName;

    private Integer anonAvatar;

    /** 真实作者，仅后台可见 */
    private Long realUserId;

    private String realNickname;

    private Integer likeCount;

    private Integer commentCount;

    /** 1正常 0隐藏 */
    private Integer status;

    private LocalDateTime createdAt;
}
