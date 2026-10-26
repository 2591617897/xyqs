package com.campus.common.upload;

import java.util.Set;

/**
 * 上传文件的真实类型校验（魔数 / magic bytes）。
 *
 * <p><b>为什么看内容不看扩展名</b>：扩展名来自客户端，随手改。把
 * {@code shell.html} 改名成 {@code avatar.png} 上传，服务端如果只按后缀放行，
 * 文件就落进了 {@code /uploads} —— 而这个目录是 Nginx 直出的静态资源目录。
 * 配上浏览器把 {@code .png} 当图片解析还安全，一旦有人传的是 {@code .svg}
 * （可内嵌脚本）或服务器 MIME 推断失误，就变成存储型 XSS / 钓鱼页面托管。
 *
 * <p>现在要求「扩展名在白名单」且「文件头确实是这个格式」双条件成立。
 * 配合 {@code X-Content-Type-Options: nosniff}，基本堵死了伪装文件。
 */
public final class FileMagic {

    private FileMagic() {
    }

    /** 图片白名单 */
    public static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    /** 聊天视频白名单 */
    public static final Set<String> VIDEO_EXTS = Set.of("mp4", "mov", "m4v");

    /**
     * 判断文件头是否与扩展名相符。
     *
     * @param head 文件前若干个字节（建议 >= 16）
     * @param ext  小写扩展名，不含点
     */
    public static boolean matches(byte[] head, String ext) {
        if (head == null || head.length < 12 || ext == null) {
            return false;
        }
        switch (ext) {
            case "jpg":
            case "jpeg":
                return (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "png":
                return (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                        && (head[4] & 0xFF) == 0x0D && (head[5] & 0xFF) == 0x0A
                        && (head[6] & 0xFF) == 0x1A && (head[7] & 0xFF) == 0x0A;
            case "gif":
                return head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8';
            case "webp":
                return head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                        && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            case "mp4":
            case "m4v":
            case "mov":
                // ISO Base Media：第 4~7 字节是 "ftyp"，兼容 mp4/mov/m4v 各种 brand
                return head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
            default:
                return false;
        }
    }
}
