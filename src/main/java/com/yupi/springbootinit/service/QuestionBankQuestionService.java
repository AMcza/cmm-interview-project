package com.yupi.springbootinit.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.springbootinit.model.dto.questionbankquestion.QuestionBankQuestionQueryRequest;
import com.yupi.springbootinit.model.entity.QuestionBankQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.QuestionBankQuestionVO;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 题目服务
 *
 *
 */
public interface QuestionBankQuestionService extends IService<QuestionBankQuestion> {

    /**
     * 校验数据
     *
     * @param questionBankQuestion
     * @param add 对创建的数据进行校验
     */
    void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest);
    
    /**
     * 获取题目封装
     *
     * @param questionBankQuestion
     * @param request
     * @return
     */
    QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request);

    /**
     * 分页获取题目封装
     *
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request);


    /**
     * 批量添加题目到题库
     * @param questionIdList
     * @param questionBankId
     * @param loginUser
     */
    public void batchAddQuestionToBank(List<Long> questionIdList, Long questionBankId, User loginUser);

    //todo 优化：稳定性之 避免长事务
    @Transactional
    void batchAddQuestionToBankInner2(List<QuestionBankQuestion> questionBankQuestions);

    /**
     * 批量从题库移除题目(避免长事务版)
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionToBankInner(List<QuestionBankQuestion> questionBankQuestions);
    /**
     * 批量从题库移除题目
     * @param questionIdList
     * @param questionBankId
     */
    public void batchRemoveQuestionsFromBank(List<Long> questionIdList,Long questionBankId);


}
