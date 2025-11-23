package com.yupi.springbootinit.mapper;

import com.yupi.springbootinit.model.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
* @author HP
* @description 针对表【question(题目)】的数据库操作Mapper
* @createDate 2025-09-23 19:43:04
* @Entity com.yupi.springbootinit.model.entity.Question
*/
public interface QuestionMapper extends BaseMapper<Question> {
    /**
     * 查询题目列表(包括已被删除的数据)
     * @param minUpdateTime
     * @return
     */
    @Select("select * from question where updateTime >=#{minUpdateTime}")
    List<Question> listQuestionWithDelete(Date minUpdateTime);

}




