package com.yupi.springbootinit.model.mapper;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.springbootinit.model.dto.question.QuestionEsDTO;
import com.yupi.springbootinit.model.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface QuestionMapperStruct {


    @Mappings({
            @Mapping(source = "id", target = "id"),
            @Mapping(source = "title", target = "title"),
            @Mapping(source = "content", target = "content"),
            @Mapping(source = "answer", target = "answer"),
            @Mapping(source = "tags", target = "tags"),
            @Mapping(source = "userId", target = "userId"),
            @Mapping(source = "createTime", target = "createTime"),
            @Mapping(source = "updateTime", target = "updateTime"),
            @Mapping(source = "isDelete", target = "isDelete"),
    })
    QuestionEsDTO objToDto(Question question);

    @Mappings({
            @Mapping(source = "id", target = "id"),
            @Mapping(source = "title", target = "title"),
            @Mapping(source = "content", target = "content"),
            @Mapping(source = "answer", target = "answer"),
            @Mapping(source = "tags", target = "tags"),
            @Mapping(source = "userId", target = "userId"),
            @Mapping(source = "createTime", target = "createTime"),
            @Mapping(source = "updateTime", target = "updateTime"),
            @Mapping(source = "isDelete", target = "isDelete"),
    })
    Question dtoToObj(QuestionEsDTO questionEsDTO);

    default String listToString(List<String> list){
        if(CollUtil.isNotEmpty(list)){
            return JSONUtil.toJsonStr(list);
        }
        return null;
    }

    default List<String> stringToList(String str){
        if(StrUtil.isNotBlank(str)){
            return JSONUtil.toList(str, String.class);
        }
        return null;
    }
}
