package com.yupi.springbootinit;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import com.yupi.springbootinit.model.dto.question.QuestionEsDTO;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.mapper.QuestionMapperStruct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class MapperTests {

    @Resource
    private QuestionMapperStruct questionMapperStruct;

    @Test
    public void DtoToObj() {
        QuestionEsDTO questionEsDTO = new QuestionEsDTO();
        questionEsDTO.setId(1L);
        questionEsDTO.setTitle("questionesdto");
        questionEsDTO.setContent("测试");
        questionEsDTO.setAnswer("测试");
        List<String> tags = new ArrayList<>();
        tags.add("java");
        tags.add("spring");
        tags.add("python");
        questionEsDTO.setTags(tags);
        questionEsDTO.setUserId(0L);
        questionEsDTO.setCreateTime(new Date());
        questionEsDTO.setUpdateTime(new Date());
        questionEsDTO.setIsDelete(0);
        System.out.println(questionEsDTO);
        Question question = questionMapperStruct.dtoToObj(questionEsDTO);
        System.out.println(question);
    }
    @Test
    public void ObjToDto() {
        Question question=new Question();
        question.setId(2L);
        question.setTitle("question");
        question.setContent("测试");
        question.setTags("[java,python,c++]");
        question.setAnswer("测试大难");
        question.setUserId(0L);
        question.setEditTime(new Date());
        question.setCreateTime(new Date());
        question.setUpdateTime(new Date());
        question.setIsDelete(0);
        System.out.println(question);
        QuestionEsDTO questionEsDTO = questionMapperStruct.objToDto(question);
        System.out.println(questionEsDTO);
    }
}
