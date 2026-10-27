package com.campus.post.dto;

import lombok.Data;

/**
 * 评论者资料（一次性批量查，避免评论列表 N+1）。
 *
 * <p>昵称/头像取 t_user 的**最新值**：t_post_comment 里的 user_name / user_avatar
 * 是发表时的快照，用户改昵称改头像后评论里还是旧值，与「我的」页对不上
 * （与 UserSnapshotMapper 覆盖帖子快照是同一个道理）。
 *
 * <p>province / city 来自用户所属学校（t_school），用于评论下方展示省份。
 * 用户没绑学校时 province 与 city 都为空，前端就不显示这一段。
 */
@Data
public class CommentUserBrief {

    private Long id;

    private String nickname;

    private String avatar;

    private Long schoolId;

    /** 学校所在省份，如「贵州」 */
    private String province;

    /** 学校所在城市，省份为空时兜底 */
    private String city;
}
