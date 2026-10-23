package com.campus.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花 ID 是 19 位 Long，超出了 JavaScript 的安全整数范围（2^53-1）。
 * 如果直接以数字返回，小程序 / 浏览器 JSON.parse 会丢精度，
 * 表现为「列表里有数据，点进详情 404」。
 *
 * 这里把所有 Long / long 序列化成字符串，前端当字符串透传即可。
 * 注意：实体里的计数（点赞数、评论数等）用的是 Integer，不受影响。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
