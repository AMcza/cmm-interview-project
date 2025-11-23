package com.yupi.springbootinit.job.cycle;

import cn.hutool.core.collection.CollUtil;
import com.yupi.springbootinit.esdao.QuestionEsDao;
import com.yupi.springbootinit.mapper.QuestionMapper;
import com.yupi.springbootinit.model.dto.question.QuestionEsDTO;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.mapper.QuestionMapperStruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class IncSyncQuestionToEs {

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionEsDao questionEsDao;

    @Resource
    private QuestionMapperStruct questionMapperStruct;

    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60*1000)
    public void run() {
        //查询近5分钟内的数据
        long FIVE_MINUTES=5*60*1000L;
        //五分钟前的时间
        Date fiveMinutesAgoDate=new Date(new Date().getTime()-FIVE_MINUTES);

        List<Question> questionList=questionMapper.listQuestionWithDelete(fiveMinutesAgoDate);

        if(CollUtil.isEmpty(questionList)){
            log.info("没有需要同步的数据");
            return;
        }
        List<QuestionEsDTO> questionEsDTOList=questionList.stream()
                .map(questionMapperStruct::objToDto)
                .collect(Collectors.toList());
        final int pageSize=500;
        int total=questionEsDTOList.size();
        log.info("同步数据 start,total {}",total);
        for(int i=0;i<total;i+=pageSize){
            int end=Math.min(i+pageSize,total);
            log.info("sync from {} to {}",i,end);
            questionEsDao.saveAll(questionEsDTOList.subList(i,end));
        }
        log.info("同步数据 end");
    }
}
