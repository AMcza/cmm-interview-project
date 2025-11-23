package com.yupi.springbootinit.model.dto.questionbankquestion;


import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class QuestionBankQuestionBatchAddRequest implements Serializable {

    private Long questionBankId;

    private List<Long> questionIdList;

    private static final long serialVersionUID = 1L;
}
