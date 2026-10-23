package com.campus.wall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.api.R;
import com.campus.common.exception.BizException;
import com.campus.common.security.UserContext;
import com.campus.common.social.Like;
import com.campus.common.social.mapper.LikeMapper;
import com.campus.wall.dto.CommentReq;
import com.campus.wall.dto.WallPostReq;
import com.campus.wall.entity.WallComment;
import com.campus.wall.entity.WallPost;
import com.campus.wall.mapper.WallCommentMapper;
import com.campus.wall.mapper.WallPostMapper;
import com.campus.wall.mapper.SchoolRegionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WallService {

    private static final String TARGET = "WALL";
    private static final String[] ANON_PREFIX = {"匿名同学", "路过的同学", "一位同学", "神秘同学"};
    private static final Set<String> CATEGORIES = Set.of("love", "tree", "find", "findp");

    private final WallPostMapper postMapper;
    private final WallCommentMapper commentMapper;
    private final SchoolRegionMapper schoolRegionMapper;
    private final LikeMapper likeMapper;

    public R.Page<WallPost> page(Long schoolId, Long userId, long page, long size) {
        return page(schoolId, userId, page, size, null, null);
    }

    /**
     * @param sort     null / "new" 按发布时间倒序；"hot" 按点赞数倒序
     * @param category 分类过滤：love / tree / find / findp，空则不过滤
     */
    public R.Page<WallPost> page(Long schoolId, Long userId, long page, long size, String sort, String category) {
        return pageInternal(schoolId, null, userId, page, size, sort, category);
    }

    /** 学校所在市（未配置/未知时为 null） */
    public String cityOfSchool(Long schoolId) {
        return schoolId == null ? null : schoolRegionMapper.selectCityBySchoolId(schoolId);
    }

    /** 市域范围：同一城市所有高校的表白 */
    public R.Page<WallPost> pageByCity(String city, Long userId, long page, long size, String sort, String category) {
        return pageInternal(null, city, userId, page, size, sort, category);
    }

    private R.Page<WallPost> pageInternal(Long schoolId, String city, Long userId,
                                          long page, long size, String sort, String category) {
        List<Long> citySchoolIds = null;
        if (StringUtils.hasText(city)) {
            citySchoolIds = schoolRegionMapper.selectSchoolIdsByCity(city.trim());
            if (citySchoolIds.isEmpty()) {
                return R.Page.of(java.util.List.of(), 0, page, size);
            }
        }
        LambdaQueryWrapper<WallPost> q = Wrappers.<WallPost>lambdaQuery()
                .eq(WallPost::getStatus, 1)
                .eq(schoolId != null, WallPost::getSchoolId, schoolId)
                .in(citySchoolIds != null, WallPost::getSchoolId, citySchoolIds)
                .eq(StringUtils.hasText(category), WallPost::getCategory, normalizeCategory(category));
        if ("hot".equalsIgnoreCase(sort)) {
            q.orderByDesc(WallPost::getLikeCount).orderByDesc(WallPost::getCreatedAt);
        } else {
            q.orderByDesc(WallPost::getCreatedAt);
        }
        Page<WallPost> p = postMapper.selectPage(new Page<>(page, size), q);
        Set<Long> liked = likedIds(p.getRecords().stream().map(WallPost::getId).toList(), userId);
        p.getRecords().forEach(post -> {
            decorate(post, userId);
            post.setLiked(liked.contains(post.getId()));
        });
        return R.Page.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public WallPost detail(Long id, Long userId) {
        WallPost post = postMapper.selectById(id);
        if (post == null || (post.getStatus() != null && post.getStatus() == 0)) {
            throw BizException.notFound("这条表白不见了");
        }
        decorate(post, userId);
        post.setLiked(!likedIds(List.of(id), userId).isEmpty());
        return post;
    }

    /**
     * 发布匿名表白。user_id 只入库不返回，对外用派生匿名昵称。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long publish(WallPostReq req) {
        Long userId = UserContext.requireVerifiedUserId();
        WallPost post = new WallPost();
        post.setUserId(userId);
        post.setSchoolId(UserContext.schoolId());
        post.setContent(req.getContent().trim());
        post.setImages(join(req.getImages()));
        post.setCategory(normalizeCategory(req.getCategory()));
        post.setStatus(1);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setAnonName(anonName(userId, 0L));
        post.setAnonAvatar(anonAvatar(userId, 0L));
        postMapper.insert(post);

        // 用帖子 id 再派生一次，保证同一个人在不同帖子下昵称不同，避免被串联
        post.setAnonName(anonName(userId, post.getId()));
        post.setAnonAvatar(anonAvatar(userId, post.getId()));
        postMapper.updateById(post);
        return post.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long postId, Long userId) {        WallPost post = postMapper.selectById(postId);
        if (post == null) {
            throw BizException.notFound("这条表白不见了");
        }
        Like exist = likeMapper.selectOne(Wrappers.<Like>lambdaQuery()
                .eq(Like::getTargetType, TARGET)
                .eq(Like::getTargetId, postId)
                .eq(Like::getUserId, userId));
        if (exist != null) {
            likeMapper.deleteById(exist.getId());
            postMapper.incrLike(postId, -1);
            return false;
        }
        Like like = new Like();
        like.setTargetType(TARGET);
        like.setTargetId(postId);
        like.setUserId(userId);
        likeMapper.insert(like);
        postMapper.incrLike(postId, 1);
        return true;
    }

    public List<WallComment> comments(Long postId, Long userId) {
        List<WallComment> all = commentMapper.selectList(Wrappers.<WallComment>lambdaQuery()
                .eq(WallComment::getPostId, postId)
                .orderByAsc(WallComment::getCreatedAt)
                .last("limit 500"));
        // 内存组装成两层树：顶层评论 + 各自的回复
        Map<Long, List<WallComment>> replyMap = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(WallComment::getParentId));
        List<WallComment> top = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .toList();
        for (WallComment c : top) {
            c.setMine(userId != null && userId.equals(c.getUserId()));
            c.setImageList(splitImages(c.getImages()));
            List<WallComment> replies = replyMap.getOrDefault(c.getId(), List.of());
            replies.forEach(r -> {
                r.setMine(userId != null && userId.equals(r.getUserId()));
                r.setImageList(splitImages(r.getImages()));
            });
            c.setReplies(replies);
            c.setReplyCount(replies.size());
        }
        return top;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addComment(Long postId, CommentReq req) {
        Long userId = UserContext.requireVerifiedUserId();
        WallPost post = postMapper.selectById(postId);
        if (post == null || (post.getStatus() != null && post.getStatus() == 0)) {
            throw BizException.notFound("这条表白不见了");
        }
        String content = req.getContent() == null ? "" : req.getContent().trim();
        String images = join(req.getImages());
        // 支持「只发图不写字」的评论
        if (!StringUtils.hasText(content) && !StringUtils.hasText(images)) {
            throw new BizException("说点什么，或者配张图吧");
        }
        WallComment comment = new WallComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(req.getParentId() == null ? 0L : req.getParentId());
        comment.setContent(content);
        comment.setImages(images);
        // 回复评论：记录被回复人的匿名昵称快照，供「A 回复 B」展示
        if (comment.getParentId() > 0) {
            WallComment parent = commentMapper.selectById(comment.getParentId());
            if (parent == null || !postId.equals(parent.getPostId())) {
                throw new BizException("要回复的评论不存在");
            }
            comment.setReplyToName(parent.getAnonName());
        } else {
            comment.setReplyToName(null);
        }
        comment.setAnonName(anonName(userId, postId));
        commentMapper.insert(comment);
        postMapper.incrComment(postId, 1);
        comment.setMine(true);
        comment.setImageList(splitImages(images));
        return comment.getId();
    }

    /**
     * 删除自己的评论（他人评论不可删）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long postId, Long commentId) {
        Long userId = UserContext.requireUserId();
        WallComment comment = commentMapper.selectById(commentId);
        if (comment == null || !postId.equals(comment.getPostId())) {
            throw BizException.notFound("评论不存在或已删除");
        }
        if (!userId.equals(comment.getUserId())) {
            throw BizException.forbidden("只能删除自己的评论");
        }
        // 删除顶层评论时，级联删除其下的全部回复
        int removed = 0;
        if (comment.getParentId() == null || comment.getParentId() == 0) {
            List<WallComment> replies = commentMapper.selectList(Wrappers.<WallComment>lambdaQuery()
                    .eq(WallComment::getParentId, commentId));
            if (!replies.isEmpty()) {
                commentMapper.deleteByIds(replies.stream().map(WallComment::getId).toList());
                removed = replies.size();
            }
        }
        commentMapper.deleteById(commentId);
        postMapper.incrComment(postId, -(1 + removed));
    }

    private List<String> splitImages(String images) {
        return StringUtils.hasText(images)
                ? Arrays.stream(images.split(",")).filter(StringUtils::hasText).toList()
                : List.of();
    }

    /**
     * 编辑自己的表白内容（匿名昵称、发布时间保持不变）。
     */
    public void update(Long id, WallPostReq req) {
        Long userId = UserContext.requireVerifiedUserId();
        WallPost post = postMapper.selectById(id);
        if (post == null || (post.getStatus() != null && post.getStatus() == 0)) {
            throw BizException.notFound("这条表白不见了");
        }
        if (!userId.equals(post.getUserId())) {
            throw BizException.forbidden("只能编辑自己发布的内容");
        }
        // 用 UpdateWrapper 显式 set，保证「清空图片」也能写回 null
        postMapper.update(null, Wrappers.<WallPost>lambdaUpdate()
                .eq(WallPost::getId, id)
                .set(WallPost::getContent, req.getContent().trim())
                .set(WallPost::getImages, join(req.getImages()))
                .set(WallPost::getCategory, normalizeCategory(req.getCategory())));
    }

    public void delete(Long postId) {
        Long userId = UserContext.requireUserId();
        WallPost post = postMapper.selectById(postId);
        if (post == null) {
            throw BizException.notFound("这条表白不见了");
        }
        if (!userId.equals(post.getUserId())) {
            throw BizException.forbidden("只能删除自己发布的内容");
        }
        post.setStatus(0);
        postMapper.updateById(post);
    }

    // ---------------- 内部方法 ----------------

    /** 分类白名单校验，非法或空值回落到 love（精选表白） */
    private String normalizeCategory(String category) {
        return StringUtils.hasText(category) && CATEGORIES.contains(category) ? category : "love";
    }

    private void decorate(WallPost post, Long userId) {
        post.setImageList(StringUtils.hasText(post.getImages())
                ? Arrays.stream(post.getImages().split(",")).filter(StringUtils::hasText).toList()
                : List.of());
        post.setMine(userId != null && userId.equals(post.getUserId()));
        if (post.getLikeCount() == null) {
            post.setLikeCount(0);
        }
        if (post.getCommentCount() == null) {
            post.setCommentCount(0);
        }
    }

    private Set<Long> likedIds(List<Long> postIds, Long userId) {
        if (userId == null || postIds.isEmpty()) {
            return Set.of();
        }
        return likeMapper.selectList(Wrappers.<Like>lambdaQuery()
                        .eq(Like::getTargetType, TARGET)
                        .eq(Like::getUserId, userId)
                        .in(Like::getTargetId, postIds))
                .stream().map(Like::getTargetId).collect(Collectors.toSet());
    }

    private String join(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream().filter(StringUtils::hasText).limit(9).collect(Collectors.joining(","));
    }

    private String anonName(Long userId, Long seed) {
        int n = Math.floorMod(Long.hashCode(userId * 31 + seed), 997);
        String prefix = ANON_PREFIX[Math.floorMod(n, ANON_PREFIX.length)];
        return prefix + "·" + String.format("%03d", n);
    }

    private Integer anonAvatar(Long userId, Long seed) {
        return Math.floorMod(Long.hashCode(userId * 17 + seed * 3), 8) + 1;
    }
}
