package com.campus.post.service;

/**
 * 用户等级（LV）规则 —— 评论昵称旁的等级徽章。
 *
 * <p><b>LV 由真实活跃值算出，不是随机数</b>：活跃值 = 发帖数 + 评论数（含楼中楼回复）。
 * 计数来自 t_post / t_post_comment 的真实行数，用户发得越多、聊得越多，等级越高。
 *
 * <p>门槛表（达到即升一级）：
 * <pre>
 *   LV1  0-2      LV7   46-65     LV13  271-340
 *   LV2  3-6      LV8   66-90     LV14  341-420
 *   LV3  7-12     LV9   91-120    LV15  421-510
 *   LV4  13-20    LV10  121-160   LV16  511-610
 *   LV5  21-30    LV11  161-210   LV17  611-720
 *   LV6  31-45    LV12  211-270   LV18  721-840
 * </pre>
 * 门槛呈近似二次增长：越往后升一级越难，避免出现「LV999」这种灌水号。
 *
 * <p>规则只在这一处定义，改口径改这个文件即可，别在别处再写一份。
 */
public final class UserLevel {

    /** 升一级所需的活跃值门槛；下标 i 对应「LV(i+2)」 */
    private static final int[] THRESHOLDS = {
            3, 7, 13, 21, 31, 46, 66, 91, 121,
            161, 211, 271, 341, 421, 511, 611, 721, 841, 971, 1111
    };

    private UserLevel() {
    }

    /**
     * 活跃值 → 等级。
     *
     * @param activity 发帖数 + 评论数，负数按 0 算
     * @return 等级，最小 1
     */
    public static int of(int activity) {
        int safe = Math.max(0, activity);
        int lv = 1;
        for (int threshold : THRESHOLDS) {
            if (safe < threshold) {
                break;
            }
            lv++;
        }
        return lv;
    }
}
