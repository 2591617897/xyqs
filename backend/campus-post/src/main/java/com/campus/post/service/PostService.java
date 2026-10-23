package com.campus.post.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.api.R;
import com.campus.common.client.UserClient;
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
    private final LikeMapper likeMapper;
    private final UserClient userClient;

    public List<Channel> channels(Long schoolId) {
        LambdaQueryWrapper<Channel> w = Wrappers.lambdaQuery();
        if (schoolId != null) {
            w.and(q -> q.isNull(Channel::getSchoolId).or().eq(Channel::getSchoolId, schoolId));
        }
        w.orderByDesc(Channel::getSort);
        return channelMapper.selectList(w);
    }

    public R.Page<Post> page(Long channelId, Long schoolId, long page, long size, Long userId) {
        Page<Post> p = postMapper.selectPage(new Page<>(page, size), Wrappers.<Post>lambdaQuery()
                .eq(Post::getStatus, 1)
                .eq(channelId != null && channelId > 0, Post::getChannelId, channelId)
                .eq(schoolId != null, Post::getSchoolId, schoolId)
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
        return true;
    }

    public List<PostComment> comments(Long postId, Long userId) {
        List<PostComment> list = commentMapper.selectList(Wrappers.<PostComment>lambdaQuery()
                .eq(PostComment::getPostId, postId)
                .orderByAsc(PostComment::getCreatedAt)
                .last("limit 300"));
        list.forEach(c -> {
            c.setMine(userId != null && userId.equals(c.getUserId()));
            c.setImageList(splitImages(c.getImages()));
        });
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addComment(Long postId, String content, List<String> images) {
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
        return comment.getId();
    }

    /** 删除自己的评论 */
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
        commentMapper.deleteById(commentId);
        postMapper.incrComment(postId, -1);
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
