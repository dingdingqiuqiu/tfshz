package com.tfsh.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tfsh.Service.IBlogCommentsService;
import com.tfsh.Service.IUserService;
import com.tfsh.dto.Result;
import com.tfsh.dto.UserDTO;
import com.tfsh.entity.BlogComments;
import com.tfsh.entity.User;
import com.tfsh.utils.UserHolder;
import com.tfsh.utils.RedisConstants;
import cn.hutool.core.bean.BeanUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取博客的所有评论（一级评论）
     */
    @GetMapping("/of/blog")
    public Result queryBlogComments(@RequestParam("blogId") Long blogId, @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<BlogComments> page = new Page<>(current, 10);
        Page<BlogComments> result = blogCommentsService.query()
                .eq("blog_id", blogId)
                .eq("parent_id", 0)
                .eq("status", false)
                .orderByDesc("create_time")
                .page(page);

        // 补充用户信息
        List<Map<String, Object>> comments = result.getRecords().stream().map(comment -> {
            Map<String, Object> map = BeanUtil.beanToMap(comment);
            User user = userService.getById(comment.getUserId());
            if (user != null) {
                map.put("userName", user.getNickName());
                map.put("icon", user.getIcon());
            }
            // 当前登录用户是否点过赞，前端按钮据此显示已赞状态
            map.put("isLiked", isLiked(comment.getId()));
            return map;
        }).collect(Collectors.toList());

        return Result.ok(comments);
    }

    /**
     * 获取评论的所有回复
     */
    @GetMapping("/reply/{parentId}")
    public Result queryCommentReplies(@PathVariable("parentId") Long parentId) {
        List<BlogComments> replies = blogCommentsService.query()
                .eq("parent_id", parentId)
                .eq("status", false)
                .orderByAsc("create_time")
                .list();

        // 补充用户信息
        List<Map<String, Object>> replyList = replies.stream().map(reply -> {
            Map<String, Object> map = BeanUtil.beanToMap(reply);
            User user = userService.getById(reply.getUserId());
            if (user != null) {
                map.put("userName", user.getNickName());
                map.put("icon", user.getIcon());
            }
            map.put("isLiked", isLiked(reply.getId()));
            return map;
        }).collect(Collectors.toList());

        return Result.ok(replyList);
    }

    /**
     * 评论点赞 / 取消点赞（同一用户再点一次即取消）。
     * 和笔记点赞一样用 Redis ZSet 做“一人一赞”去重：comment:liked:{commentId} -> {userId: 时间戳}
     */
    @PutMapping("/like/{id}")
    public Result likeComment(@PathVariable("id") Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        BlogComments comment = blogCommentsService.getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }

        String key = RedisConstants.COMMENT_LIKED_KEY + id;
        String userId = user.getId().toString();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId);
        if (score == null) {
            // 未点赞 -> 点赞：数据库计数 +1，Redis 记录用户
            boolean success = blogCommentsService.update()
                    .setSql("liked = liked + 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
            }
            return Result.ok(likedOf(id));
        }
        // 已点赞 -> 取消：计数 -1（用 GREATEST 兜住脏数据，避免出现负数）
        boolean success = blogCommentsService.update()
                .setSql("liked = GREATEST(liked - 1, 0)").eq("id", id).update();
        if (success) {
            stringRedisTemplate.opsForZSet().remove(key, userId);
        }
        return Result.ok(likedOf(id));
    }

    /** 当前登录用户是否点过赞；未登录一律 false */
    private boolean isLiked(Long commentId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return false;
        }
        Double score = stringRedisTemplate.opsForZSet()
                .score(RedisConstants.COMMENT_LIKED_KEY + commentId, user.getId().toString());
        return score != null;
    }

    /** 返回最新点赞数与当前用户的点赞状态，前端据此刷新按钮 */
    private Map<String, Object> likedOf(Long commentId) {
        BlogComments latest = blogCommentsService.getById(commentId);
        Map<String, Object> data = new HashMap<>(2);
        data.put("liked", latest == null ? 0 : latest.getLiked());
        data.put("isLiked", isLiked(commentId));
        return data;
    }

    /**
     * 发表评论
     */
    @PostMapping
    public Result addComment(@RequestBody BlogComments comment) {
        // 获取当前登录用户（LoginInterceptor 已拦截未登录，这里只是兜底）
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }

        String content = comment.getContent() == null ? "" : comment.getContent().trim();
        if (content.isEmpty()) {
            return Result.fail("评论内容不能为空");
        }
        // content 列是 varchar(255)，超长会直接抛 SQL 异常
        if (content.length() > 255) {
            return Result.fail("评论内容不能超过 255 字");
        }
        comment.setContent(content);

        comment.setUserId(currentUser.getId());
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        comment.setStatus(false);
        comment.setLiked(0);

        // 一级评论：parentId 和 answerId 都是 0。
        // 表里 answer_id 是 NOT NULL 且没有 DEFAULT，MyBatis-Plus 默认不插入 null 字段，
        // 不显式赋值就会报 “Field 'answer_id' doesn't have a default value”，整条评论存不进去。
        if (comment.getParentId() == null) {
            comment.setParentId(0L);
        }
        if (comment.getAnswerId() == null) {
            comment.setAnswerId(0L);
        }

        boolean saved = blogCommentsService.save(comment);
        if (!saved) {
            return Result.fail("发表评论失败");
        }
        return Result.ok();
    }
}



