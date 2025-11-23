package com.yupi.springbootinit.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.QuestionBankQuestionMapper;
import com.yupi.springbootinit.model.dto.questionbankquestion.QuestionBankQuestionQueryRequest;
import com.yupi.springbootinit.model.entity.Question;
import com.yupi.springbootinit.model.entity.QuestionBank;
import com.yupi.springbootinit.model.entity.QuestionBankQuestion;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.QuestionBankQuestionVO;
import com.yupi.springbootinit.model.vo.UserVO;
import com.yupi.springbootinit.service.QuestionBankQuestionService;
import com.yupi.springbootinit.service.QuestionBankService;
import com.yupi.springbootinit.service.QuestionService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 题目服务实现
 *
 *
 */
@Service
@Slf4j
public class QuestionBankQuestionServiceImpl extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion> implements QuestionBankQuestionService {

    @Resource
    private UserService userService;

    @Resource //存在循环依赖
    @Lazy
    private QuestionService questionService;

    @Resource
    private QuestionBankService questionBankService;

    /**
     * 校验数据
     *
     * @param questionBankQuestion
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add) {
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.PARAMS_ERROR);
        //不需要校验
//        // todo 从对象中取值
//        Long questionBankId = questionBankQuestion.getQuestionBankId();
//        Long questionId = questionBankQuestion.getQuestionId();
//        Long userId = questionBankQuestion.getUserId();
//
//        // 创建数据时，参数不能为空
//        if (add) {
//            // todo 补充校验规则
//            ThrowUtils.throwIf(StringUtils.isBlank(questionBankId), ErrorCode.PARAMS_ERROR,"questionBankId不能为空");
//        }
//        // 修改数据时，有参数则校验
//        // todo 补充校验规则
//        if (StringUtils.isNotBlank(title)) {
//            ThrowUtils.throwIf(title.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
//        }
        //校验题目是否存在
        Long questionId= questionBankQuestion.getQuestionId();
        if(questionId!=null){
            Question question = questionService.getById(questionId);
            ThrowUtils.throwIf(question==null,ErrorCode.PARAMS_ERROR,"题目不存在");
        }
        Long questionBankId=questionBankQuestion.getQuestionBankId();
        if(questionBankId!=null){
            QuestionBank questionBank = questionBankService.getById(questionBankId);
            ThrowUtils.throwIf(questionBank==null,ErrorCode.PARAMS_ERROR,"题库不存在");
        }
    }

    /**
     * 获取查询条件
     *
     * @param questionBankQuestionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest questionBankQuestionQueryRequest) {
        QueryWrapper<QuestionBankQuestion> queryWrapper = new QueryWrapper<>();
        if (questionBankQuestionQueryRequest == null) {
            return queryWrapper;
        }
        // todo 从对象中取值
        Long id = questionBankQuestionQueryRequest.getId();
        Long notId = questionBankQuestionQueryRequest.getNotId();
        Long questionBankId = questionBankQuestionQueryRequest.getQuestionBankId();
        Long questionId = questionBankQuestionQueryRequest.getQuestionId();
        Long userId = questionBankQuestionQueryRequest.getUserId();
        String sortField = questionBankQuestionQueryRequest.getSortField();
        String sortOrder = questionBankQuestionQueryRequest.getSortOrder();

        // 精确查询
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionBankId),"questionBankId",questionBankId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId),"questionId",questionId);
        // 排序规则
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 获取题目封装
     *
     * @param questionBankQuestion
     * @param request
     * @return
     */
    @Override
    public QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion, HttpServletRequest request) {
        // 对象转封装类
        QuestionBankQuestionVO questionBankQuestionVO = QuestionBankQuestionVO.objToVo(questionBankQuestion);

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Long userId = questionBankQuestion.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionBankQuestionVO.setUser(userVO);

        // endregion

        return questionBankQuestionVO;
    }

    /**
     * 分页获取题目封装
     *
     * @param questionBankQuestionPage
     * @param request
     * @return
     */
    @Override
    public Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> questionBankQuestionPage, HttpServletRequest request) {
        List<QuestionBankQuestion> questionBankQuestionList = questionBankQuestionPage.getRecords();
        Page<QuestionBankQuestionVO> questionBankQuestionVOPage = new Page<>(questionBankQuestionPage.getCurrent(), questionBankQuestionPage.getSize(), questionBankQuestionPage.getTotal());
        if (CollUtil.isEmpty(questionBankQuestionList)) {
            return questionBankQuestionVOPage;
        }
        // 对象列表 => 封装对象列表
        List<QuestionBankQuestionVO> questionBankQuestionVOList = questionBankQuestionList.stream().map(questionBankQuestion -> {
            return QuestionBankQuestionVO.objToVo(questionBankQuestion);
        }).collect(Collectors.toList());

        // todo 可以根据需要为封装对象补充值，不需要的内容可以删除
        // region 可选
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionBankQuestionList.stream().map(QuestionBankQuestion::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 填充信息
        questionBankQuestionVOList.forEach(questionBankQuestionVO -> {
            Long userId = questionBankQuestionVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionBankQuestionVO.setUser(userService.getUserVO(user));
        });
        // endregion

        questionBankQuestionVOPage.setRecords(questionBankQuestionVOList);
        return questionBankQuestionVOPage;
    }


    /**
     * 批量添加题目到题库
     * @param questionIdList
     * @param questionBankId
     * @param loginUser
     */
    @Override
    //@Transactional(rollbackFor = Exception.class)
    public void batchAddQuestionToBank(List<Long> questionIdList, Long questionBankId, User loginUser) {
        //参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR,"题目列表为空");
        ThrowUtils.throwIf(questionBankId==null || questionBankId<=0, ErrorCode.PARAMS_ERROR,"题库id为空");
        ThrowUtils.throwIf(loginUser==null,ErrorCode.NOT_LOGIN_ERROR);
        //检查题目id是否存在
        LambdaQueryWrapper<Question> questionLambdaQueryWrapper = Wrappers.lambdaQuery(Question.class)
                .select(Question::getId)
                .in(Question::getId, questionIdList);
        //检查题目id是否存在
        //List<Question> questionList=questionService.listByIds(questionIdList);---->select *
        List<Question> questionList=questionService.list(questionLambdaQueryWrapper);
        //合法的题目id
        List<Long> validQuestionIdList=questionList.stream()
                .map(Question::getId)
                .collect(Collectors.toList());


        //todo 优化:检查那些题目还不存在于题库中，避免重复插入
        LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper=Wrappers.lambdaQuery(QuestionBankQuestion.class)
                            .eq(QuestionBankQuestion::getQuestionBankId,questionBankId)
                            .in(QuestionBankQuestion::getQuestionId,validQuestionIdList);
        List<QuestionBankQuestion> existQuestionList=this.list(lambdaQueryWrapper);
        //已存在于题库中的题库id
        Set<Long> existQuestionIdSet=existQuestionList.stream()
                        .map(QuestionBankQuestion::getQuestionId)
                        .collect(Collectors.toSet());
        //已存在于题库中的题库id,不需要再次添加
        validQuestionIdList=validQuestionIdList.stream().filter(questionId->{
            return !existQuestionIdSet.contains(questionId);
        }).collect(Collectors.toList());
        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR,"所有的题目都存在于题库中");


        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR,"题目id不存在");
        //检查题目id是否存在
        ThrowUtils.throwIf(CollUtil.isEmpty(validQuestionIdList), ErrorCode.PARAMS_ERROR,"合法的题目列表为空");
        //检查题库id是否存在
        QuestionBank questionBank=questionBankService.getById(questionBankId);
        ThrowUtils.throwIf(questionBank==null,ErrorCode.NOT_FOUND_ERROR,"题库不存在");
        //执行插入 todo :分批插入避免长事务
        //       todo: 异步处理

        //自定义线程池
        ThreadPoolExecutor costomExecutor=new ThreadPoolExecutor(
                20,                         //核心线程数
                50,                                     //最大线程数
                60L,                                     //线程空闲存活时间
                TimeUnit.SECONDS,                        //存活时间单位
                new LinkedBlockingQueue<>(1000),//阻塞队列容量
                new ThreadPoolExecutor.CallerRunsPolicy()//拒绝策略:由调度线程处理任务
        );
        //保存所有批次任务
        List<CompletableFuture<Void>> futures=new ArrayList<>();

        int batchSize=1000;
        int totalQuestionListSize=validQuestionIdList.size();
        for(int i=0;i<totalQuestionListSize;i+=batchSize) {
            //生成每批次的数据
            List<Long> subList=validQuestionIdList.subList(i,Math.min(i+batchSize,totalQuestionListSize));
            List<QuestionBankQuestion> questionBankQuestions=subList.stream().map(questionId->{
                QuestionBankQuestion questionBankQuestion=new QuestionBankQuestion();
                questionBankQuestion.setQuestionBankId(questionBankId);
                questionBankQuestion.setQuestionId(questionId);
                questionBankQuestion.setUserId(loginUser.getId());
                return questionBankQuestion;
            }).collect(Collectors.toList());
            //使用事务处理每批次数据 todo 代理：保证事务生效
            QuestionBankQuestionService questionBankQuestionService=(QuestionBankQuestionService) AopContext.currentProxy();

            //异步处理分批数据,将任务添加到异步任务列表
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                questionBankQuestionService.batchAddQuestionToBankInner(questionBankQuestions);
            }, costomExecutor).exceptionally(ex->{
               log.error("批处理任务执行失败");
               return null;
            });
            futures.add(future);
            //questionBankQuestionService.batchAddQuestionToBankInner(questionBankQuestions);
        }
        //等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        //关闭线程池
        costomExecutor.shutdown();
    }

    //todo 优化：稳定性之 避免长事务
    //todo 优化：健壮性--异常处理,明确抛给服务端和客户端信息
    @Override
    @Transactional
    public void batchAddQuestionToBankInner2(List<QuestionBankQuestion> questionBankQuestions) {
        for(QuestionBankQuestion questionBankQuestion:questionBankQuestions){
            long questionId=questionBankQuestion.getQuestionId();
            long questionBankId=questionBankQuestion.getQuestionBankId();
            try {
                boolean result=this.save(questionBankQuestion);
                if (!result) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
                }
            }catch (DataIntegrityViolationException e){
                log.error("数据库唯一键冲突或违反其他完整性约束,题库id:{},题库id:{},错误信息:{}",questionBankId,questionId,e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已经存在于该题库,无法重复添加");
            }catch (DataAccessException e){
                log.error("数据库连接问题，事务问题等导致操作失败,题目id:{},题库id:{},错误信息:{}",questionId,questionBankId,e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库连接问题，事务问题等导致操作失败");
            }catch (Exception e){
                log.error("添加题库到题库时发生未知错误,题库id:{},题库id:{},错误信息:{}",questionBankId,questionId,e.getMessage());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "添加题库到题库时发生未知错误");
            }
        }
    }

    @Override
    @Transactional
    public void batchAddQuestionToBankInner(List<QuestionBankQuestion> questionBankQuestions) {

        try {
            boolean result = this.saveBatch(questionBankQuestions);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "向题库添加题目失败");
        }catch (DataIntegrityViolationException e){
            log.error("数据库唯一键冲突或违反其他完整性约束,错误信息:{}",e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目已经存在于该题库,无法重复添加");
        }catch (DataAccessException e){
            log.error("数据库连接问题，事务问题等导致操作失败,错误信息:{}",e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据库连接问题，事务问题等导致操作失败");
        }catch (Exception e){
            log.error("添加题库到题库时发生未知错误,错误信息:{}",e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "添加题库到题库时发生未知错误");
        }

    }
    /**
     * 批量从题库删除题目
     * @param questionIdList
     * @param questionBankId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemoveQuestionsFromBank(List<Long> questionIdList, Long questionBankId) {
        //参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(questionIdList), ErrorCode.PARAMS_ERROR,"题目列表为空");
        ThrowUtils.throwIf(questionBankId==null || questionBankId<=0, ErrorCode.PARAMS_ERROR,"题库id为空");
        //执行删除关联
        for(Long questionId:questionIdList){
            //构造查询
            LambdaQueryWrapper<QuestionBankQuestion> lambdaQueryWrapper= Wrappers.lambdaQuery(QuestionBankQuestion.class)
                    .eq(QuestionBankQuestion::getQuestionId,questionId)
                    .eq(QuestionBankQuestion::getQuestionBankId,questionBankId);
            boolean result=this.remove(lambdaQueryWrapper);
            if(!result){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"删除失败");
            }
        }
    }
}
