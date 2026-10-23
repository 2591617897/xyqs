package com.campus.wall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class WallPostReq {

    @NotBlank(message = "说点什么吧")
    @Size(max = 1000, message = "内容不超过 1000 字")
    private String content;

    private List<String> images;

    /** 分类：love / tree / find / findp，缺省 love */
    private String category;
}
