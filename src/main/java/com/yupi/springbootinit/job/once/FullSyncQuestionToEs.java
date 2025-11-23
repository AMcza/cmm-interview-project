package com.yupi.springbootinit.job.once;

import cn.hutool.core.collection.CollUtil;
import com.yupi.springbootinit.esdao.QuestionEsDao;
import com.yupi.springbootinit.model.dto.question.QuestionEsDTO;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.mapper.QuestionMapperStruct;
import com.yupi.springbootinit.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 同步Mysql数据到Es
 */
//@Component
@Slf4j
public class FullSyncQuestionToEs implements CommandLineRunner {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionEsDao questionEsDao;

    @Resource
    private QuestionMapperStruct questionMapperStruct;

    @Override
    public void run(String... args) {
        try {
            log.info("FullSyncQuestionToEs: Starting full sync from MySQL to Elasticsearch...");

            List<Question> questionList = questionService.list();

            if (CollUtil.isEmpty(questionList)) {
                log.info("FullSyncQuestionToEs: No questions found in DB, skip sync.");
                return;
            }

            int total = questionList.size();
            log.info("FullSyncQuestionToEs: Found {} questions, converting to DTO...", total);

            List<QuestionEsDTO> dtoList = questionList.stream()
                    .map(questionMapperStruct::objToDto)
                    .collect(Collectors.toList());

            final int pageSize = 500;
            log.info("FullSyncQuestionToEs: Start syncing in batches of {}, total: {}", pageSize, total);

            for (int i = 0; i < total; i += pageSize) {
                int end = Math.min(i + pageSize, total);
                List<QuestionEsDTO> subList = dtoList.subList(i, end);
                questionEsDao.saveAll(subList);
                log.info("FullSyncQuestionToEs: Synced batch from {} to {}", i, end);
            }

            log.info("FullSyncQuestionToEs: Sync completed successfully, total: {}", total);
        } catch (Exception e) {
            log.error("FullSyncQuestionToEs: Sync failed", e);
        }
    }
}