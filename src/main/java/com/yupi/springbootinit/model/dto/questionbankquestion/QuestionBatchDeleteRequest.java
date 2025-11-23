package com.yupi.springbootinit.model.dto.questionbankquestion;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
@Data
public class QuestionBatchDeleteRequest implements Serializable {
    /**
     * 题目id列表
     */
    private List<Long> questionIdList;

    private static final long serialVersionUID=1L;
}
