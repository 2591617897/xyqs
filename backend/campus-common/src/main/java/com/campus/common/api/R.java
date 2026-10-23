package com.campus.common.api;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一响应体。code = 0 表示成功。
 */
@Data
public class R<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 0;
        r.msg = "ok";
        r.data = data;
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    /**
     * 分页结构，避免每个服务重复定义。
     */
    @Data
    public static class Page<T> implements Serializable {
        private List<T> records;
        private long total;
        private long page;
        private long size;
        private boolean hasMore;

        public static <T> Page<T> of(List<T> records, long total, long page, long size) {
            Page<T> p = new Page<>();
            p.records = records;
            p.total = total;
            p.page = page;
            p.size = size;
            p.hasMore = page * size < total;
            return p;
        }
    }
}
