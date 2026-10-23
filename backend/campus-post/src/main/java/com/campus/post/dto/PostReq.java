package com.campus.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PostReq {

    @NotNull(message = "请选择频道")
    private Long channelId;

    @NotBlank(message = "请填写标题")
    @Size(max = 80, message = "标题不超过 80 字")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = 3000, message = "正文不超过 3000 字")
    private String content;

    private List<String> images;
}
