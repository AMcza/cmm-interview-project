package com.yupi.springbootinit.model.dto.questionbankquestion;

import lombok.Data;

import java.io.Serializable;

@Data
public class QuestionBankQuestionRemoveRequest implements Serializable {

    /**
     * 题库id
     */
    private Long questionBankId;

    /**
     * 题目id
     */
    private Long questionId;

    private static final long serialVersionUID = 1L;
}
