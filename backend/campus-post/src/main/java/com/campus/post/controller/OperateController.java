package com.campus.post.controller;

import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.common.security.UserContext;
import com.campus.post.dto.AdMaterialVO;
import com.campus.post.dto.NoticeVO;
import com.campus.post.service.OperateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页运营位读取：<b>广告素材</b>与<b>后台公告</b>。
 *
 * <p>这两件事以前是「后台能配、但小程序永远看不到」的断裂状态——
 * 数据在 t_ad_slot / t_ad_material / t_admin_notice 里躺着，
 * 却没有任何对外接口去消费它。这里补上消费端，让运营的投放真正跑到用户眼前。
 *
 * <p>接口由网关公开（只读 GET，不带登录也能看）：
 * 游客打开首页就该看到 Banner 和公告，否则首屏永远缺一块。
 */
@RestController
@RequiredArgsConstructor
public class OperateController {

    private final OperateService operateService;

    /**
     * 拉取指定广告位当前该展示的素材。
     *
     * <p>返回空列表是<b>正常结果</b>，不是错误：广告位没配、没过审、不在投放期、
     * 或者定向范围没命中当前用户，首页都按「没有广告」渲染。
     *
     * @param code 广告位编码，如 home_banner
     * @param city 用户所在城市，用于匹配「城市定向」的广告位；可空
     */
    @GetMapping("/ads/{code}")
    public R<List<AdMaterialVO>> materials(@PathVariable("code") String code,
                                           @RequestParam(required = false, name = "city") String city) {
        return R.ok(operateService.materials(code, city));
    }

    /**
     * 广告点击上报（不校验登录：未登录用户点了广告也该计入曝光效果）。
     *
     * <p>只做「存在才计数」，素材 id 不存在时静默忽略。
     */
    @PostMapping("/ads/materials/{id}/click")
    public R<Void> click(@PathVariable("id") Long id) {
        operateService.click(id);
        return R.ok();
    }

    /** 公告列表：紧急 > 重要 > 普通，同级按发布时间倒序 */
    @GetMapping("/notices")
    public R<List<NoticeVO>> notices(@RequestParam(required = false, name = "schoolId") Long schoolId) {
        return R.ok(operateService.notices(schoolId));
    }

    /**
     * 本次进入小程序该弹出的公告；不弹时 data 为 null。
     *
     * <p><b>公开接口</b>：游客也要能看到弹窗（运营的强提醒不能只对大号生效），
     * 但游客没有回执，频次由客户端本地存储判定（见 miniprogram/utils/notice.js）。
     * data 为 null 是正常结果，前端不做任何提示。
     */
    @GetMapping("/notices/popup")
    public R<NoticeVO> popup() {
        return R.ok(operateService.popup(UserContext.userId()));
    }

    /**
     * 「我知道了」：记录确认回执，之后 popup_mode=3 的公告不再打扰该用户。
     *
     * <p>未登录时静默成功——不落库即可，前端本地已记住，没必要给游客报 401。
     */
    @PostMapping("/notices/{id:\\d+}/confirm")
    public R<Void> confirm(@PathVariable("id") Long id) {
        operateService.confirm(id, UserContext.userId());
        return R.ok();
    }

    /** 公告详情：路径限定为纯数字，避免和 /notices/popup 抢路由 */
    @GetMapping("/notices/{id:\\d+}")
    public R<NoticeVO> notice(@PathVariable("id") Long id,
                              @RequestParam(required = false, name = "schoolId") Long schoolId) {
        NoticeVO vo = operateService.notice(id, schoolId);
        if (vo == null) {
            throw BizException.notFound("公告不存在或已下线");
        }
        return R.ok(vo);
    }
}
