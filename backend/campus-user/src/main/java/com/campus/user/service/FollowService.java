package com.campus.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.common.exception.BizException;
import com.campus.user.dto.FollowResp;
import com.campus.user.dto.FollowUserVO;
import com.campus.user.entity.Follow;
import com.campus.user.entity.User;
import com.campus.user.mapper.FollowMapper;
import com.campus.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 关注 / 取消关注 / 关注状态查询。
 *
 * 计数事务策略（见设计文档 1.4）：
 *   单事务内完成「关系 insert/delete + 两条计数 UPDATE」；
 *   计数 SQL 用 greatest(x + delta, 0) 防负数；
 *   uk_pair 唯一键兜底幂等，重复插入的 DuplicateKeyException 按已关注处理，不重复计数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    /**
     * 关注。幂等：已关注直接返回成功，不重复计数。
     *
     * @param followerId 关注者（当前登录用户）
     * @param followeeId 被关注者
     * @return {following:true, fansCount} fansCount 为被关注者最新粉丝数
     */
    @Transactional(rollbackFor = Exception.class)
    public FollowResp follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BizException("不能关注自己");
        }
        User followee = userMapper.selectById(followeeId);
        if (followee == null || (followee.getStatus() != null && followee.getStatus() == 0)) {
            throw BizException.notFound("对方不存在或已被封禁");
        }
        if (followMapper.countPair(followerId, followeeId) == 0) {
            Follow follow = new Follow();
            follow.setFollowerId(followerId);
            follow.setFolloweeId(followeeId);
            try {
                followMapper.insert(follow);
            } catch (DuplicateKeyException e) {
                // uk_pair 并发兜底：并发双插时后到者插入失败（MySQL error 1062），
                // 说明关系行已由并发事务建立。必须按「已关注」直接返回并跳过任何计数
                // UPDATE——否则关系行只有 1 条，而两条事务各 +1 导致 fans/follow 漂移。
                // 粉丝数用加锁读取最新已提交值：普通快照读可能落后于已提交的并发事务。
                log.info("关注关系已存在，按幂等处理 followerId={}, followeeId={}", followerId, followeeId);
                FollowResp dup = new FollowResp();
                dup.setFollowing(true);
                dup.setFansCount(nz(userMapper.selectFansCountForUpdate(followeeId)));
                return dup;
            }
            userMapper.incrFans(followeeId, 1);
            userMapper.incrFollow(followerId, 1);
        }
        FollowResp resp = new FollowResp();
        resp.setFollowing(true);
        resp.setFansCount(fansCountOf(followeeId));
        return resp;
    }

    /**
     * 取消关注。幂等：关系不存在也返回成功。
     *
     * 并发自查（QA 要求核查同类问题）：unfollow 由 DELETE 的受影响行数驱动计数，
     * 删除是原子的——并发双删时只有一方 removed=1（行锁串行化），另一方 removed=0
     * 不动计数；与并发 follow 交错时「删行 -1 / 插行 +1」也守恒。
     * 不存在 follow 的插入竞态，因此无需类似的 DuplicateKey 兜底。
     *
     * @return {following:false, fansCount} fansCount 为被关注者最新粉丝数
     */
    @Transactional(rollbackFor = Exception.class)
    public FollowResp unfollow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BizException("不能关注自己");
        }
        int removed = followMapper.delete(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        if (removed > 0) {
            userMapper.incrFans(followeeId, -1);
            userMapper.incrFollow(followerId, -1);
        }
        FollowResp resp = new FollowResp();
        resp.setFollowing(false);
        resp.setFansCount(fansCountOf(followeeId));
        return resp;
    }

    /**
     * 关注状态查询。未登录（followerId 为空）/ 本人 → false。
     */
    public boolean status(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null || followerId.equals(followeeId)) {
            return false;
        }
        return followMapper.countPair(followerId, followeeId) > 0;
    }

    /** 读被关注者最新粉丝数（同一事务内能看到本事务自己的 +1） */
    private Integer fansCountOf(Long followeeId) {
        User user = userMapper.selectById(followeeId);
        if (user == null || user.getFansCount() == null) {
            return 0;
        }
        return user.getFansCount();
    }

    /** 计数字段兜底：空值统一为 0 */
    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 列表：我关注的人 / 我的粉丝。
     *
     * @param me   当前登录用户 id
     * @param type following = 我关注的；fans = 关注我的
     */
    public List<FollowUserVO> list(Long me, String type) {
        if ("fans".equalsIgnoreCase(type)) {
            return followMapper.listFans(me);
        }
        return followMapper.listFollowing(me);
    }
}
