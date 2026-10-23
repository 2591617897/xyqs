package com.campus.message.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 会话列表里的身份小标签 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeerTag {

    private String text;

    /** mint 绿 / orange 橙 / pink 粉 / plain 灰 */
    private String cls;
}
