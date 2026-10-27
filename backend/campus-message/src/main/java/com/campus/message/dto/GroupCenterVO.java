package com.campus.message.dto;

import lombok.Data;

import java.util.List;

/**
 * 消息页群区域的整体状态。
 *
 * <p>为什么要包一层 {@code needSchool}：{@code schoolId = null} 的用户
 * （注册了但没选学校）不能给他一个空群 —— 那是「这 app 没人用」的错觉。
 * 这里明确告诉前端「先引导去选学校」，前端跳 school-onboard。
 */
@Data
public class GroupCenterVO {

    /** true = 用户还没绑定学校，前端应显示选校引导而不是群卡片 */
    private Boolean needSchool;

    /** 我的群（needSchool=false 时一定有值） */
    private GroupVO group;

    /** 学校名字（选校引导文案用） */
    private String schoolName;

    /** 未登录时为 true（前端显示「登录后加入校友群」） */
    private Boolean needLogin;
}
