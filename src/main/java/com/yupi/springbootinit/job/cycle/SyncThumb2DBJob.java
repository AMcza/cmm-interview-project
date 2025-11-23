package com.yupi.springbootinit.job.cycle;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.springbootinit.constant.ThumbConstant;
import com.yupi.springbootinit.mapper.PostMapper;
import com.yupi.springbootinit.model.entity.PostThumb;
import com.yupi.springbootinit.model.enums.ThumbTypeEnum;
import com.yupi.springbootinit.service.PostThumbService;
import com.yupi.springbootinit.utils.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

//@Component
@Slf4j
public class SyncThumb2DBJob {

    @Resource
    private PostThumbService  postThumbService;
    @Resource
    private PostMapper postMapper;

    @Resource
    private RedissonClient redissonClient;

    @Scheduled(fixedRate = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void run(){
        log.info("开始执行Redisson版本点赞数据同步");
        DateTime nowDate= DateUtil.date();
        //如果秒数0-9,则回到上一分钟的50秒
        int second=(DateUtil.second(nowDate)/10-1)*10;
        if(second==-10){
            second=50;
            //回到上一分钟
            nowDate=DateUtil.offsetMinute(nowDate,-1);
        }
        String date=DateUtil.format(nowDate,"HH:mm")+second;
        syncThumb2DBByDate(date);
        log.info("临时数据同步完成");

    }

    public void syncThumb2DBByDate(String dateKey){
        //获取临时点赞数据
        String tempThumbKey= RedisKeyUtil.getTempThumbKey(dateKey);
        RMap<String,Integer> tempThumbMap=redissonClient.getMap(tempThumbKey);

        //判断数据是否为空
        boolean thumbMapEmpty=tempThumbMap.isEmpty();
        Map<String,Integer> allTempThumbMap=new HashMap<>(tempThumbMap);
        //批量处理点赞数据
        Map<Long,Long> postThumbCountMap=new HashMap<>();
        if(thumbMapEmpty){
            return;
        }

        ArrayList<PostThumb> thumbList=new ArrayList<>();
        LambdaQueryWrapper<PostThumb> wrapper=new LambdaQueryWrapper<>();
        boolean needRemove=false;
        for(Map.Entry<String,Integer> entry:allTempThumbMap.entrySet()){
            String userIdPostId=entry.getKey();
            Integer thumbType=entry.getValue();

            String[] userIdAndPostId=userIdPostId.split(":");
            Long userId=Long.valueOf(userIdAndPostId[0]);
            Long postId=Long.valueOf(userIdAndPostId[1]);
            if(thumbType== ThumbTypeEnum.INCR.getValue()){
                //点赞
                PostThumb postThumb=new PostThumb();
                postThumb.setUserId(userId);
                postThumb.setPostId(postId);
                postThumb.setCreateTime(new Date());
                thumbList.add(postThumb);
            }else if(thumbType== ThumbTypeEnum.DECR.getValue()){
                //取消点赞
                needRemove=true;
                wrapper.or().eq(PostThumb::getUserId,userId).eq(PostThumb::getPostId,postId);
            }else{
                log.warn("数据异常:{}",userId+","+postId+","+thumbType);
            }
            //计算点赞增量
            postThumbCountMap.put(postId,postThumbCountMap.getOrDefault(postId,0L)+thumbType);
        }
        //批量插入点赞数据
        if(!thumbList.isEmpty()){
            postThumbService.saveBatch(thumbList);
            log.info("批量插入{}条点赞记录",thumbList.size());
        }
        //批量删除取消点赞记录
        if(needRemove){
            int removeCount=postThumbService.remove(wrapper)?1:0;
            log.info("批量删除{}条点赞记录",removeCount);
        }
        //批量更新帖子点赞量
        if(!postThumbCountMap.isEmpty()){

        }
        //异步删除已处理的临时数据
        CompletableFuture.runAsync(()->{
            tempThumbMap.delete();
        });
    }


    private void batchUpdatePostThumbCount(Map<Long,Long> postThumbCountMap){

    }
    private String getCurrentDateKey(){
        long currrentTime=System.currentTimeMillis();
        long batchTime=(currrentTime/10000)*10000;
        return String.valueOf(batchTime);
    }
}
