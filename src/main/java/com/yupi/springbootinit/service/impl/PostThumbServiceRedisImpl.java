package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.ThumbConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.PostThumbMapper;
import com.yupi.springbootinit.model.entity.Post;
import com.yupi.springbootinit.model.entity.PostThumb;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.enums.ThumbTypeEnum;
import com.yupi.springbootinit.model.vo.cache.ThumbCacheVO;
import com.yupi.springbootinit.service.PostService;
import com.yupi.springbootinit.service.PostThumbService;
import com.yupi.springbootinit.utils.RedisKeyUtil;
import org.redisson.api.*;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 帖子点赞服务实现
 */
@Service("postthumbServiceRedis")
public class PostThumbServiceRedisImpl extends ServiceImpl<PostThumbMapper, PostThumb>
        implements PostThumbService {

    @Resource
    private PostService postService;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 点赞/取消点赞一体化方法
     *
     * @param postId
     * @param loginUser
     * @return 1:点赞成功, -1:取消点赞成功
     */
    @Override
    public int doPostThumb(long postId, User loginUser) {
        // 判断实体是否存在，根据类别获取实体
        Post post = postService.getById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 每个用户串行点赞
        long userId = loginUser.getId();
        PostThumbService postThumbService = (PostThumbService) AopContext.currentProxy();
        synchronized (String.valueOf(userId).intern()) {
            return postThumbService.doPostThumbInner(userId, postId);
        }
    }

    /**
     * 封装了事务的方法 - 整合点赞和取消点赞
     *
     * @param userId
     * @param postId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int doPostThumbInner(long userId, long postId) {
        // 1. 获取帖子信息
        Post post = postService.getById(postId);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");

        Date createTime = post.getCreateTime();
        long now = System.currentTimeMillis();
        long createTimeMillis = createTime.getTime();
        boolean isHotPost = (now - createTimeMillis) < (30L * 24 * 60 * 60 * 1000); // 30天内发布的帖子

        // 2. 检查是否已点赞
        boolean alreadyThumbed = checkIfThumbed(userId, postId, isHotPost);

        if (alreadyThumbed) {
            // 取消点赞
            return cancelThumb(userId, postId, isHotPost);
        } else {
            // 点赞
            return addThumb(userId, postId, isHotPost);
        }
    }

    /**
     * 检查是否已点赞
     */
    private boolean checkIfThumbed(long userId, long postId, boolean isHotPost) {
        String bfKey = userId + ":" + postId;

        // 布隆过滤器检查
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bloom:postThumb");
        bloomFilter.tryInit(1000000, 0.01);
        boolean mightExist = bloomFilter.contains(bfKey);

        if (isHotPost) {
            // 热点帖子：优先查询Redis
            RMap<String, String> userThumbMap = redissonClient.getMap(ThumbConstant.USER_THUMB_KEY_PREFIX + userId);
            String thumbJson = userThumbMap.get(String.valueOf(postId));
            if (thumbJson != null) {
                ThumbCacheVO cacheVO = JSONUtil.toBean(thumbJson, ThumbCacheVO.class);
                if (cacheVO.getExpireTime() > System.currentTimeMillis()) {
                    return true;
                } else {
                    // 已过期，从Redis中移除
                    userThumbMap.remove(String.valueOf(postId));
                }
            }
        }

        // 检查临时点赞数据
        if (checkTempThumbData(userId, postId)) {
            return true;
        }

        // 未命中缓存或冷帖，根据布隆过滤器决定是否查询MySQL
        if (!mightExist && !isHotPost) {
            // 冷帖且未命中布隆过滤器，直接返回未点赞
            return false;
        } else {
            // 查询MySQL
            QueryWrapper<PostThumb> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("postId", postId).eq("userId", userId);
            PostThumb existingThumb = this.getOne(queryWrapper);
            return existingThumb != null;
        }
    }

    /**
     * 检查临时点赞数据
     */
    private boolean checkTempThumbData(long userId, long postId) {
        String dateKey = getCurrentDateKey();
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(dateKey);
        String userPostKey = userId + ":" + postId;

        RMap<String, Integer> tempThumbMap = redissonClient.getMap(tempThumbKey);
        Integer thumbType = tempThumbMap.get(userPostKey);

        // 如果临时数据中存在且为点赞状态，返回true
        return thumbType != null && thumbType == ThumbTypeEnum.DECR.getValue();
    }

    /**
     * 添加点赞
     */
    private int addThumb(long userId, long postId, boolean isHotPost) {
        // 1. 记录到Redis临时存储
        recordThumbToRedis(userId, postId, ThumbTypeEnum.INCR.getValue()); // 1表示点赞

        // 2. 更新布隆过滤器
        String bfKey = userId + ":" + postId;
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter("bloom:postThumb");
        bloomFilter.tryInit(1000000, 0.01);
        bloomFilter.add(bfKey);

        // 3. 如果是热点帖子，更新缓存
        if (isHotPost) {
            Post post = postService.getById(postId);
            long expireTime = post.getCreateTime().getTime() + 30L * 24 * 60 * 60 * 1000;

            ThumbCacheVO cacheVO = new ThumbCacheVO();
            cacheVO.setThumbId(-1L); // 临时ID，实际ID会在同步时生成
            cacheVO.setExpireTime(expireTime);

            RMap<String, String> userThumbMap = redissonClient.getMap(ThumbConstant.USER_THUMB_KEY_PREFIX + userId);
            userThumbMap.put(String.valueOf(postId), JSONUtil.toJsonStr(cacheVO));
        }

        return 1;
    }

    /**
     * 取消点赞
     */
    private int cancelThumb(long userId, long postId, boolean isHotPost) {
        // 1. 记录到Redis临时存储
        recordThumbToRedis(userId, postId, ThumbTypeEnum.DECR.getValue()); // -1表示取消点赞

        // 2. 如果是热点帖子，更新缓存
        if (isHotPost) {
            RMap<String, String> userThumbMap = redissonClient.getMap(ThumbConstant.USER_THUMB_KEY_PREFIX + userId);
            userThumbMap.remove(String.valueOf(postId));
        }

        return -1;
    }

    /**
     * 记录点赞操作到Redis临时存储（使用Redisson）
     */
    private void recordThumbToRedis(long userId, long postId, int thumbType) {
        String dateKey = getCurrentDateKey();
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(dateKey);
        String userPostKey = userId + ":" + postId;

        // 使用Redisson的RMap存储临时点赞数据
        RMap<String, Integer> tempThumbMap = redissonClient.getMap(tempThumbKey);
        tempThumbMap.put(userPostKey, thumbType);

        // 设置过期时间（1小时）
        tempThumbMap.expire(1, TimeUnit.HOURS);

        // 同时更新帖子点赞数的增量缓存
        updatePostThumbIncrement(postId, thumbType);
    }

    /**
     * 更新帖子点赞数增量缓存
     */
    private void updatePostThumbIncrement(long postId, int increment) {
        String thumbIncrementKey = ThumbConstant.TEMP_THUMB_KEY_PREFIX + postId;
        RAtomicLong atomicLong = redissonClient.getAtomicLong(thumbIncrementKey);

        if (increment == 1) {
            atomicLong.incrementAndGet();
        } else if (increment == -1) {
            atomicLong.decrementAndGet();
        }

        // 设置过期时间
        atomicLong.expire(1, TimeUnit.HOURS);
    }

    /**
     * 获取当前时间键（与定时任务保持一致格式）
     */
    private String getCurrentDateKey() {
        // 使用与SyncThumb2DBJob相同的时间逻辑
        // 每10秒一个批次，格式为 "HH:mm:ss" 但只保留十位数
        long currentTime = System.currentTimeMillis();
        // 对齐到10秒的倍数
        long batchTime = (currentTime / 10000) * 10000;
        return String.valueOf(batchTime);
    }

    /**
     * 是否点赞帖子（增强版，包含临时数据检查）
     */
    @Override
    public Boolean hasThumb(Long postId, Long userId) {
        // 1. 先检查临时点赞数据
        if (checkTempThumbData(userId, postId)) {
            return true;
        }

        // 2. 检查Redis缓存中的持久化数据
        String key = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
        RMap<String, String> map = redissonClient.getMap(key);
        String thumbJson = map.get(postId.toString());

        if (thumbJson != null) {
            ThumbCacheVO cacheVO = JSONUtil.toBean(thumbJson, ThumbCacheVO.class);
            // 检查是否过期
            if (cacheVO.getExpireTime() > System.currentTimeMillis()) {
                return true;
            } else {
                // 已过期，移除缓存
                map.remove(postId.toString());
            }
        }

        // 3. 检查MySQL数据库
        QueryWrapper<PostThumb> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("postId", postId).eq("userId", userId);
        return this.count(queryWrapper) > 0;
    }
}