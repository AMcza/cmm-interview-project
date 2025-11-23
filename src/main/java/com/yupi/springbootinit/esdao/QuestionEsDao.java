package com.yupi.springbootinit.esdao;

import com.yupi.springbootinit.model.dto.question.QuestionEsDTO;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface QuestionEsDao extends ElasticsearchRepository<QuestionEsDTO,Long> {
    //自动映射为查询操作

    /**
     *
     * @param userId
     * @return
     */
    List<QuestionEsDTO> findByUserId(Long userId);
}
