package com.campus.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AuthConfig {

    /**
     * 通用 HTTP 客户端（只用于 GET，如 code2Session / 逆地理）。
     *
     * <p><b>不要用它发带请求体的 POST 给微信</b>：底层 HttpURLConnection 在流式写出、
     * Content-Length 未知时会用 <b>分块传输（chunked）</b>，微信 {@code msg_sec_check} /
     * {@code media_check_async} 对 chunked 请求直接返回 <b>HTTP 412 且无响应体</b>
     * （不是 errcode，而是连接层拒绝）。JdkClientHttpRequestFactory 也不行，
     * 它自带 OutputStreamBodyPublisher，长度同样未知。所以内容安全那三个接口
     * 在 {@code WxSecurityService} 里直接用 JDK HttpClient（BodyPublisher 一定带定长）。
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }
}
