package com.yupi.springbootinit.model.dto.question;

import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
@Data
@Document(indexName = "question")
public class QuestionEsDTO implements Serializable {

    private static final String DATE_TIME_PATTERN="yyyy-MM-dd HH:mm:ss";
    /**
     * id
     */
    private Long id;
    /**
     * 标题
     */
    private String title;
    /**
     * 内容
     */
    private String content;
    /**
     * 答案
     */
    private String answer;
    /**
     * 标签列表
     */
    private List<String> tags;
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 创建时间
     */
    @Field(type = FieldType.Date, format = {},pattern = DATE_TIME_PATTERN)
    private Date createTime;
    /**
     * 更新时间
     */
    @Field(type = FieldType.Date, format = {},pattern = DATE_TIME_PATTERN)
    private Date updateTime;

    /**
     * 是否删除
     */
    private Integer isDelete;

    private static final long serialVersionUID = 1L;


}
