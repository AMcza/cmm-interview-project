package com.yupi.springbootinit.model.dto.questionbankquestion;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
@Data
public class QuestionBankQuestionBatchRemoveRequest implements Serializable {
    /**
     * 题库id
     */
    private Long questionBankId;
    /**
     * 题目id列表
     */
    private List<Long> questionIdList;

    private static final long serialVersionUID=1L;

}
