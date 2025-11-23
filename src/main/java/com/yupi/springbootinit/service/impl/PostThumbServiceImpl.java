package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSON;
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
import com.yupi.springbootinit.model.vo.cache.ThumbCacheVO;
import com.yupi.springbootinit.service.PostService;
import com.yupi.springbootinit.service.PostThumbService;
import javax.annotation.Resource;
import javax.swing.plaf.synth.SynthTextAreaUI;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;

/**
 * 帖子点赞服务实现
 *
 *
 */
@Service("postthumbService")
public class PostThumbServiceImpl extends ServiceImpl<PostThumbMapper, PostThumb>
        implements PostThumbService {

    @Resource
    private PostService postService;


    @Resource
    private RedissonClient redissonClient;
    /**
     * 点赞
     *
     * @param postId
     * @param loginUser
     * @return
     */
    @Override
    public int doPostThumb(long postId, User loginUser) {
        // 判断实体是否存在，根据类别获取实体
        Post post = postService.getById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 是否已点t
        long userId = loginUser.getId();
        // 每个用户串行点赞
        // 锁必须要包裹住事务方法
        PostThumbService postThumbService = (PostThumbService) AopContext.currentProxy();
        synchronized (String.valueOf(userId).intern()) {
            return postThumbService.doPostThumbInner(userId, postId);
        }
    }

    /**
     * 封装了事务的方法
     *
     * @param userId
     * @param postId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int doPostThumbInner(long userId, long postId) {
        //1.获取帖子信息
        Post post=postService.getById(postId);
        ThrowUtils.throwIf(post==null,ErrorCode.NOT_FOUND_ERROR,"帖子不存在");
        Date createTime = post.getCreateTime();
        long now= System.currentTimeMillis();
        long createTimeMillis=createTime.getTime();
        boolean isHotPost=(now-createTimeMillis)<(30L * 24 * 60 *60*1000); //30天内发布的帖子


        //2.布隆过滤器
        RBloomFilter<String> bloomFilter=redissonClient.getBloomFilter("bloom:postThumb");
        String bfKey=userId+":"+postId;
        bloomFilter.tryInit(1000000,0.01);
        boolean mightExist=bloomFilter.contains(bfKey);

        //3.查询是否已点赞
        boolean alreadyThumbed=false;
        PostThumb existingThumb=null;

        if(isHotPost){
            //热点,优先查询redis
            RMap<String,String> userthumbMap=redissonClient.getMap(ThumbConstant.USER_THUMB_KEY_PREFIX+userId);
            String thumbJson=userthumbMap.get(String.valueOf(postId));
            if(thumbJson!=null){
                ThumbCacheVO cacheVO= JSONUtil.toBean((String) thumbJson, ThumbCacheVO.class);
                if(cacheVO.getExpireTime()>now){
                    //封装redis中的数据
                    alreadyThumbed=true;
                    existingThumb=new PostThumb();
                    existingThumb.setId(cacheVO.getThumbId());
                }else{
                    //已过期,从redis中移除
                    userthumbMap.remove(String.valueOf(postId));
                }
            }
        }

        //未命中缓存 或 冷贴,查询Mysql
        if(!alreadyThumbed){
            if(!mightExist && !isHotPost){
                //冷贴,且未命中布隆过滤器,直接返回 -->不存在,没有点过赞
                alreadyThumbed=false;
            }else{
                //查DB
                QueryWrapper<PostThumb> queryWrapper=new QueryWrapper<>();
                queryWrapper.eq("postId",postId).eq("userId",userId);
                existingThumb=this.getOne(queryWrapper);
                alreadyThumbed=(existingThumb!=null);
            }
        }

        //4.点赞
        if(alreadyThumbed){
            //取消点赞
            boolean removed=this.removeById(existingThumb.getId());
            if(removed){
                boolean updated=postService.update()
                        .eq("id",postId)
                        .gt("thumbNum",0)
                        .setSql("thumbNum=thumbNum-1")
                        .update();
                if(updated && isHotPost){
                    //热点,更新缓存
                    redissonClient.getMap("thumb:"+userId).remove(String.valueOf(postId));
                }
                return -1;
            }
        }else{
            //点赞
            PostThumb postThumb=new PostThumb();
            postThumb.setUserId(userId);
            postThumb.setPostId(postId);
            boolean saved=this.save(postThumb);
            if(saved){
                boolean updated=postService.update()
                        .eq("id",postId)
                        .setSql("thumbNum=thumbNum+1")
                        .update();


                
                if(updated){
                    if(isHotPost){
                        //热点,更新缓存,写入Redis
                        long expireTime=createTimeMillis+30L*24*60*60*1000;
                        ThumbCacheVO cacheVO=new ThumbCacheVO();
                        cacheVO.setThumbId(postThumb.getId());
                        cacheVO.setExpireTime(expireTime);

                        RMap<String,String> userthumbMap=redissonClient.getMap(ThumbConstant.USER_THUMB_KEY_PREFIX+userId);
                        userthumbMap.put(String.valueOf(postId),JSONUtil.toJsonStr(cacheVO));
                    }

                    //加入布隆过滤器(幂等操作)
                    bloomFilter.add(bfKey);

                    return 1;
                }
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR,"点赞操作失败");
    }

    /**
     * 是否点赞帖子
     * @param postId
     * @param userId
     * @return
     */
    @Override
    public Boolean hasThumb(Long postId, Long userId) {
        String key= ThumbConstant.USER_THUMB_KEY_PREFIX+userId;
        RMap<String,Object> map=redissonClient.getMap(key);
        return map.containsKey(postId.toString());
    }

}




