package com.campus.post.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.api.R;
import com.campus.common.client.FollowClient;
import com.campus.common.client.NotificationClient;
import com.campus.common.client.UserClient;
import com.campus.common.client.dto.NotifyReq;
import com.campus.common.client.dto.UserBrief;
import com.campus.common.exception.BizException;
import com.campus.common.security.UserContext;
import com.campus.common.social.Like;
import com.campus.common.social.mapper.LikeMapper;
import com.campus.post.entity.Channel;
import com.campus.post.entity.Post;
import com.campus.post.entity.PostComment;
import com.campus.post.mapper.ChannelMapper;
import com.campus.post.mapper.PostCommentMapper;
import com.campus.post.mapper.PostMapper;
import com.campus.post.mapper.SchoolRegionMapper;
import com.campus.post.dto.PostReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private static final String TARGET = "POST";

    private final PostMapper postMapper;
    private final ChannelMapper channelMapper;
    private final PostCommentMapper commentMapper;
    private final SchoolRegionMapper schoolRegionMapper;
    private final LikeMapper likeMapper;
    private final UserClient userClient;
    private final FollowClient followClient;
    private final NotificationClient notificationClient;

    public List<Channel> channels(Long schoolId) {
        LambdaQueryWrapper<Channel> w = Wrappers.lambdaQuery();
        if (schoolId != null) {
            w.and(q -> q.isNull(Channel::getSchoolId).or().eq(Channel::getSchoolId, schoolId));
        }
        w.orderByDesc(Channel::getSort);
        return channelMapper.selectList(w);
    }

    /** 学校所在市（未配置/未知时为 null） */
    public String cityOfSchool(Long schoolId) {
        return schoolId == null ? null : schoolRegionMapper.selectCityBySchoolId(schoolId);
    }

    /** 本校范围：只看自己学校的帖子 */
    public R.Page<Post> page(Long channelId, Long schoolId, long page, long size, Long userId) {
        return pageInternal(channelId, schoolId, null, page, size, userId, null);
    }

    /** 本校范围 + 排序（sort=hot 按点赞倒序，用于首页最热帖子） */
    public R.Page<Post> page(Long channelId, Long schoolId, long page, long size, Long userId, String sort) {
        return pageInternal(channelId, schoolId, null, page, size, userId, sort);
    }

    /** 市域范围：同一城市所有高校的帖子 */
    public R.Page<Post> pageByCity(Long channelId, String city, long page, long size, Long userId) {
        return pageInternal(channelId, null, city, page, size, userId, null);
    }

    /**
     * 个人主页：按作者分页（status=1，按时间倒序）。
     * 复用 decorate / channelMap / likedIds，返回结构与信息流一致。
     */
    public R.Page<Post> pageByUser(Long userId, long page, long size, Long viewerId) {
        Page<Post> p = postMapper.selectPage(new Page<>(page, size), Wrappers.<Post>lambdaQuery()
                .eq(Post::getUserId, userId)
                .eq(Post::getStatus, 1)
                .orderByDesc(Post::getCreatedAt));
        Map<Long, Channel> channelMap = channelMap(p.getRecords());
        Set<Long> liked = likedIds(p.getRecords().stream().map(Post::getId).toList(), viewerId);
        p.getRecords().forEach(post -> {
            decorate(post, channelMap, viewerId);
            post.setLiked(liked.contains(post.getId()));
        });
        return R.Page.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 内部计数：该作者 status=1 帖子数（供个人主页聚合，campus-user Feign 调用） */
    public long countByUser(Long userId) {
        Long cnt = postMapper.selectCount(Wrappers.<Post>lambdaQuery()
                .eq(Post::getUserId, userId)
                .eq(Post::getStatus, 1));
        return cnt == null ? 0 : cnt;
    }

    private R.Page<Post> pageInternal(Long channelId, Long schoolId, String city,
                                      long page, long size, Long userId, String sort) {
        List<Long> citySchoolIds = null;
        if (StringUtils.hasText(city)) {
            citySchoolIds = schoolRegionMapper.selectSchoolIdsByCity(city.trim());
            if (citySchoolIds.isEmpty()) {
                return R.Page.of(List.of(), 0, page, size);
            }
        }
        Page<Post> p = postMapper.selectPage(new Page<>(page, size), Wrappers.<Post>lambdaQuery()
                .eq(Post::getStatus, 1)
                .eq(channelId != null && channelId > 0, Post::getChannelId, channelId)
                .eq(schoolId != null, Post::getSchoolId, schoolId)
                .in(citySchoolIds != null, Post::getSchoolId, citySchoolIds)
                .orderByDesc("hot".equalsIgnoreCase(sort), Post::getLikeCount)
                .orderByDesc(Post::getCreatedAt));

        Map<Long, Channel> channelMap = channelMap(p.getRecords());
        Set<Long> liked = likedIds(p.getRecords().stream().map(Post::getId).toList(), userId);
        p.getRecords().forEach(post -> {
            decorate(post, channelMap, userId);
            post.setLiked(liked.contains(post.getId()));
        });
        return R.Page.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    public Post detail(Long id, Long userId) {
        Post post = postMapper.selectById(id);
        if (post == null || (post.getStatus() != null && post.getStatus() == 0)) {
            throw BizException.notFound("帖子不存在或已删除");
        }
        decorate(post, channelMap(List.of(post)), userId);
        post.setLiked(!likedIds(List.of(id), userId).isEmpty());
        return post;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long publish(PostReq req) {
        Long userId = UserContext.requireVerifiedUserId();
        Channel channel = channelMapper.selectById(req.getChannelId());
        if (channel == null) {
            throw new BizException("频道不存在");
        }
        Post post = new Post();
        post.setChannelId(req.getChannelId());
        post.setUserId(userId);
        post.setSchoolId(UserContext.schoolId());
        fillAuthor(post, userId);
        post.setTitle(req.getTitle().trim());
        post.setContent(req.getContent().trim());
        post.setImages(req.getImages() == null ? null : req.getImages().stream()
                .filter(StringUtils::hasText).limit(9).collect(Collectors.joining(",")));
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setStatus(1);
        postMapper.insert(post);
        return post.getId();
    }

    /**
     * 编辑自己的帖子（作者、发布时间、点赞评论数保持不变）。
     */
    public void update(Long id, PostReq req) {
        Long userId = UserContext.requireVerifiedUserId();
        Post post = postMapper.selectById(id);
        if (post == null || (post.getStatus() != null && post.getStatus() == 0)) {
            throw BizException.notFound("帖子不存在或已删除");
        }
        if (!userId.equals(post.getUserId())) {
            throw BizException.forbidden("只能编辑自己发布的帖子");
        }
        Channel channel = channelMapper.selectById(req.getChannelId());
        if (channel == null) {
            throw new BizException("频道不存在");
        }
        // 用 UpdateWrapper 显式 set，保证清空图片能写回 null
        postMapper.update(null, Wrappers.<Post>lambdaUpdate()
                .eq(Post::getId, id)
                .set(Post::getChannelId, req.getChannelId())
                .set(Post::getTitle, req.getTitle().trim())
                .set(Post::getContent, req.getContent().trim())
                .set(Post::getImages, req.getImages() == null ? null
                        : req.getImages().stream().filter(StringUtils::hasText)
                        .limit(9).collect(Collectors.joining(","))));
    }

    public void delete(Long id) {        Long userId = UserContext.requireUserId();
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw BizException.notFound("帖子不存在");
        }
        if (!userId.equals(post.getUserId())) {
            throw BizException.forbidden("只能删除自己发布的帖子");
        }
        post.setStatus(0);
        postMapper.updateById(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long postId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw BizException.notFound("帖子不存在");
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
        // 点赞通知：跳过自赞
        tryNotifyLikePost(post, userId);
        return true;
    }

    /** 点赞帖子后异步发通知，失败不阻塞主事务 */
    private void tryNotifyLikePost(Post post, Long actorId) {
        Long receiverId = post.getUserId();
        if (receiverId == null || receiverId.equals(actorId)) return;
        NotifyReq req = new NotifyReq();
        req.setType("LIKE_POST");
        req.setReceiverId(receiverId);
        req.setActorId(actorId);
        req.setTargetType("POST");
        req.setTargetId(post.getId());
        req.setContentSnapshot(post.getTitle());
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { notificationClient.send(req); }
            catch (Exception e) { log.warn("[notify] likePost failed postId={} actorId={}: {}", post.getId(), actorId, e.getMessage()); }
        });
    }

    public List<PostComment> comments(Long postId, Long userId) {
        List<PostComment> all = commentMapper.selectList(Wrappers.<PostComment>lambdaQuery()
                .eq(PostComment::getPostId, postId)
                .orderByAsc(PostComment::getCreatedAt)
                .last("limit 500"));
        // 内存组装两层树：顶层评论 + 各自挂载的回复
        Map<Long, List<PostComment>> replyMap = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(PostComment::getParentId));
        return all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .map(c -> {
                    c.setMine(userId != null && userId.equals(c.getUserId()));
                    c.setImageList(splitImages(c.getImages()));
                    List<PostComment> replies = replyMap.getOrDefault(c.getId(), List.of());
                    replies.forEach(r -> {
                        r.setMine(userId != null && userId.equals(r.getUserId()));
                        r.setImageList(splitImages(r.getImages()));
                    });
                    c.setReplies(replies);
                    c.setReplyCount(replies.size());
                    return c;
                })
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addComment(Long postId, String content, List<String> images, Long parentId, List<Long> mentionUserIds) {
        Long userId = UserContext.requireVerifiedUserId();
        Post post = postMapper.selectById(postId);
        if (post == null || (post.getStatus() != null && post.getStatus() == 0)) {
            throw BizException.notFound("帖子不存在");
        }
        String text = content == null ? "" : content.trim();
        String imgs = join(images);
        // 支持「只发图不写字」的评论
        if (!StringUtils.hasText(text) && !StringUtils.hasText(imgs)) {
            throw new BizException("说点什么，或者配张图吧");
        }
        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId == null ? 0L : parentId);
        PostComment parentComment = null;
        // 回复评论：记录被回复人展示名快照，供「A 回复 B」展示
        if (comment.getParentId() > 0) {
            parentComment = commentMapper.selectById(comment.getParentId());
            if (parentComment == null || !postId.equals(parentComment.getPostId())) {
                throw new BizException("要回复的评论不存在");
            }
            // 楼中楼只有两层：回复「子回复」时拍平挂到其顶层评论下（否则组树时永远查不到），
            // 被回复人快照仍记目标本人，前端展示「A 回复 B」
            if (parentComment.getParentId() != null && parentComment.getParentId() > 0) {
                comment.setParentId(parentComment.getParentId());
            }
            comment.setReplyToName(StringUtils.hasText(parentComment.getUserName()) ? parentComment.getUserName() : "校园用户");
        }
        comment.setContent(text);
        comment.setImages(imgs);
        try {
            R<UserBrief> res = userClient.getBrief(userId);
            if (res != null && res.getData() != null) {
                comment.setUserName(res.getData().getNickname());
                comment.setUserAvatar(res.getData().getAvatar());
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败 userId={}", userId);
        }
        comment.setUserName(comment.getUserName() == null ? "校园用户" : comment.getUserName());
        commentMapper.insert(comment);
        postMapper.incrComment(postId, 1);
        comment.setMine(true);
        comment.setImageList(splitImages(imgs));
        // 评论通知：通知原帖作者（跳过自评）
        tryNotifyCommentPost(post, userId, text);
        // @ 通知：只能 @ 评论者已关注的人（后端强校验 + 批量发）
        tryNotifyMention(userId, mentionUserIds, "POST", post.getId(), text);
        // 楼中楼回复：同时通知被回复的那个评论作者
        if (comment.getParentId() > 0 && parentComment != null) {
            tryNotifyReplyComment("POST", post.getId(), parentComment.getUserId(), post.getUserId(), userId, text);
        }
        return comment.getId();
    }

    /** 通用 @ 通知：批量校验 + 发 MENTION 通知。校验合法的才发，失败不阻塞主事务 */
    private void tryNotifyMention(Long actorId, List<Long> mentionIds, String targetType, Long targetId, String content) {
        if (mentionIds == null || mentionIds.isEmpty()) return;
        List<Long> toValidate = mentionIds.stream().filter(id -> !id.equals(actorId)).toList();
        if (toValidate.isEmpty()) return;
        try {
            R<List<Long>> res = followClient.check(actorId, toValidate);
            if (res == null || res.getData() == null || res.getData().isEmpty()) return;
            for (Long mentionedId : res.getData()) {
                NotifyReq req = new NotifyReq();
                req.setType("MENTION");
                req.setReceiverId(mentionedId);
                req.setActorId(actorId);
                req.setTargetType(targetType);
                req.setTargetId(targetId);
                req.setContentSnapshot(content);
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try { notificationClient.send(req); }
                    catch (Exception e) { log.warn("[notify] mentionPost failed receiverId={}: {}", mentionedId, e.getMessage()); }
                });
            }
        } catch (Exception e) {
            log.warn("[notify] mentionPost check failed actorId={}: {}", actorId, e.getMessage());
        }
    }

    /** 评论帖子后通知原帖作者（跳过自评自） */
    private void tryNotifyCommentPost(Post post, Long actorId, String content) {
        Long receiverId = post.getUserId();
        if (receiverId == null || receiverId.equals(actorId)) return;
        NotifyReq req = new NotifyReq();
        req.setType("COMMENT_POST");
        req.setReceiverId(receiverId);
        req.setActorId(actorId);
        req.setTargetType("POST");
        req.setTargetId(post.getId());
        req.setContentSnapshot(content);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { notificationClient.send(req); }
            catch (Exception e) { log.warn("[notify] commentPost failed postId={}: {}", post.getId(), e.getMessage()); }
        });
    }

    /** 楼中楼回复通知：通知被回复的评论作者 */
    private void tryNotifyReplyComment(String targetType, Long targetId, Long parentAuthorId, Long contentAuthorId, Long actorId, String content) {
        if (parentAuthorId == null || parentAuthorId.equals(actorId)) return;
        if (parentAuthorId.equals(contentAuthorId)) return;
        String type = switch (targetType) {
            case "GOODS" -> "COMMENT_GOODS";
            case "POST" -> "COMMENT_POST";
            case "WALL" -> "COMMENT_WALL";
            default -> "COMMENT";
        };
        NotifyReq req = new NotifyReq();
        req.setType(type);
        req.setReceiverId(parentAuthorId);
        req.setActorId(actorId);
        req.setTargetType(targetType);
        req.setTargetId(targetId);
        req.setContentSnapshot(content);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try { notificationClient.send(req); }
            catch (Exception e) { log.warn("[notify] replyComment failed targetType={} targetId={} replyTo={}: {}", targetType, targetId, parentAuthorId, e.getMessage()); }
        });
    }

    /** 删除自己的评论（删顶层评论时级联删除其下回复） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long postId, Long commentId) {
        Long userId = UserContext.requireUserId();
        PostComment comment = commentMapper.selectById(commentId);
        if (comment == null || !postId.equals(comment.getPostId())) {
            throw BizException.notFound("评论不存在或已删除");
        }
        if (!userId.equals(comment.getUserId())) {
            throw BizException.forbidden("只能删除自己的评论");
        }
        int removed = 0;
        if (comment.getParentId() == null || comment.getParentId() == 0) {
            List<PostComment> replies = commentMapper.selectList(Wrappers.<PostComment>lambdaQuery()
                    .eq(PostComment::getParentId, commentId));
            if (!replies.isEmpty()) {
                commentMapper.deleteByIds(replies.stream().map(PostComment::getId).toList());
                removed = replies.size();
            }
        }
        commentMapper.deleteById(commentId);
        postMapper.incrComment(postId, -(1 + removed));
    }

    // ---------------- 内部方法 ----------------

    private void fillAuthor(Post post, Long userId) {
        try {
            R<UserBrief> res = userClient.getBrief(userId);
            if (res != null && res.getData() != null) {
                post.setAuthorName(res.getData().getNickname());
                post.setAuthorAvatar(res.getData().getAvatar());
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败 userId={}, {}", userId, e.getMessage());
        }
        if (!StringUtils.hasText(post.getAuthorName())) {
            post.setAuthorName("校园用户");
        }
    }

    private Map<Long, Channel> channelMap(List<Post> posts) {
        List<Long> ids = posts.stream().map(Post::getChannelId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return channelMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Channel::getId, Function.identity()));
    }

    private void decorate(Post post, Map<Long, Channel> channelMap, Long userId) {
        post.setImageList(StringUtils.hasText(post.getImages())
                ? Arrays.stream(post.getImages().split(",")).filter(StringUtils::hasText).toList()
                : List.of());
        post.setMine(userId != null && userId.equals(post.getUserId()));
        Channel channel = channelMap.get(post.getChannelId());
        if (channel != null) {
            post.setChannelName(channel.getName());
            post.setChannelIcon(channel.getIcon());
        }
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

    private List<String> splitImages(String images) {
        return StringUtils.hasText(images)
                ? Arrays.stream(images.split(",")).filter(StringUtils::hasText).toList()
                : List.of();
    }

    private String join(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream().filter(StringUtils::hasText).limit(3).collect(Collectors.joining(","));
    }
}
