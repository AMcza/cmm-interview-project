package com.yupi.springbootinit.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.PostFavourMapper;
import com.yupi.springbootinit.mapper.PostMapper;
import com.yupi.springbootinit.mapper.PostThumbMapper;
import com.yupi.springbootinit.model.dto.post.PostEsDTO;
import com.yupi.springbootinit.model.dto.post.PostQueryRequest;
import com.yupi.springbootinit.model.entity.Post;
import com.yupi.springbootinit.model.entity.PostFavour;
import com.yupi.springbootinit.model.entity.PostThumb;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.PostVO;
import com.yupi.springbootinit.model.vo.UserVO;
import com.yupi.springbootinit.model.vo.cache.ThumbCacheVO;
import com.yupi.springbootinit.service.PostFavourService;
import com.yupi.springbootinit.service.PostService;
import com.yupi.springbootinit.service.PostThumbService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.SqlUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.collection.CollUtil;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.functions.T;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

/**
 * 帖子服务实现
 *
 *
 */
@Service
@Slf4j
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    @Resource
    private UserService userService;

    @Resource
    private PostThumbMapper postThumbMapper;

    @Resource
    private PostFavourMapper postFavourMapper;

    @Resource
    private PostFavourService postFavourService;

    @Resource
    @Lazy
    private PostThumbService postThumbService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Override
    public void validPost(Post post, boolean add) {
        if (post == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String title = post.getTitle();
        String content = post.getContent();
        String tags = post.getTags();
        // 创建时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, content, tags), ErrorCode.PARAMS_ERROR);
        }
        // 有参数则校验
        if (StringUtils.isNotBlank(title) && title.length() > 80) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (StringUtils.isNotBlank(content) && content.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容过长");
        }
    }

    /**
     * 获取查询包装类
     *
     * @param postQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Post> getQueryWrapper(PostQueryRequest postQueryRequest) {
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        if (postQueryRequest == null) {
            return queryWrapper;
        }
        String searchText = postQueryRequest.getSearchText();
        String sortField = postQueryRequest.getSortField();
        String sortOrder = postQueryRequest.getSortOrder();
        Long id = postQueryRequest.getId();
        String title = postQueryRequest.getTitle();
        String content = postQueryRequest.getContent();
        List<String> tagList = postQueryRequest.getTags();
        Long userId = postQueryRequest.getUserId();
        Long notId = postQueryRequest.getNotId();
        // 拼接查询条件
        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
        }
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public Page<Post> searchFromEs(PostQueryRequest postQueryRequest) {
        Long id = postQueryRequest.getId();
        Long notId = postQueryRequest.getNotId();
        String searchText = postQueryRequest.getSearchText();
        String title = postQueryRequest.getTitle();
        String content = postQueryRequest.getContent();
        List<String> tagList = postQueryRequest.getTags();
        List<String> orTagList = postQueryRequest.getOrTags();
        Long userId = postQueryRequest.getUserId();
        // es 起始页为 0
        long current = postQueryRequest.getCurrent() - 1;
        long pageSize = postQueryRequest.getPageSize();
        String sortField = postQueryRequest.getSortField();
        String sortOrder = postQueryRequest.getSortOrder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 过滤
        boolQueryBuilder.filter(QueryBuilders.termQuery("isDelete", 0));
        if (id != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("id", id));
        }
        if (notId != null) {
            boolQueryBuilder.mustNot(QueryBuilders.termQuery("id", notId));
        }
        if (userId != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("userId", userId));
        }
        // 必须包含所有标签
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                boolQueryBuilder.filter(QueryBuilders.termQuery("tags", tag));
            }
        }
        // 包含任何一个标签即可
        if (CollUtil.isNotEmpty(orTagList)) {
            BoolQueryBuilder orTagBoolQueryBuilder = QueryBuilders.boolQuery();
            for (String tag : orTagList) {
                orTagBoolQueryBuilder.should(QueryBuilders.termQuery("tags", tag));
            }
            orTagBoolQueryBuilder.minimumShouldMatch(1);
            boolQueryBuilder.filter(orTagBoolQueryBuilder);
        }
        // 按关键词检索
        if (StringUtils.isNotBlank(searchText)) {
            boolQueryBuilder.should(QueryBuilders.matchQuery("title", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("description", searchText));
            boolQueryBuilder.should(QueryBuilders.matchQuery("content", searchText));
            boolQueryBuilder.minimumShouldMatch(1);
        }
        // 按标题检索
        if (StringUtils.isNotBlank(title)) {
            boolQueryBuilder.should(QueryBuilders.matchQuery("title", title));
            boolQueryBuilder.minimumShouldMatch(1);
        }
        // 按内容检索
        if (StringUtils.isNotBlank(content)) {
            boolQueryBuilder.should(QueryBuilders.matchQuery("content", content));
            boolQueryBuilder.minimumShouldMatch(1);
        }
        // 排序
        SortBuilder<?> sortBuilder = SortBuilders.scoreSort();
        if (StringUtils.isNotBlank(sortField)) {
            sortBuilder = SortBuilders.fieldSort(sortField);
            sortBuilder.order(CommonConstant.SORT_ORDER_ASC.equals(sortOrder) ? SortOrder.ASC : SortOrder.DESC);
        }
        // 分页
        PageRequest pageRequest = PageRequest.of((int) current, (int) pageSize);
        // 构造查询
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder().withQuery(boolQueryBuilder)
                .withPageable(pageRequest).withSorts(sortBuilder).build();
        SearchHits<PostEsDTO> searchHits = elasticsearchRestTemplate.search(searchQuery, PostEsDTO.class);
        Page<Post> page = new Page<>();
        page.setTotal(searchHits.getTotalHits());
        List<Post> resourceList = new ArrayList<>();
        // 查出结果后，从 db 获取最新动态数据（比如点赞数）
        if (searchHits.hasSearchHits()) {
            List<SearchHit<PostEsDTO>> searchHitList = searchHits.getSearchHits();
            List<Long> postIdList = searchHitList.stream().map(searchHit -> searchHit.getContent().getId())
                    .collect(Collectors.toList());
            List<Post> postList = baseMapper.selectBatchIds(postIdList);
            if (postList != null) {
                Map<Long, List<Post>> idPostMap = postList.stream().collect(Collectors.groupingBy(Post::getId));
                postIdList.forEach(postId -> {
                    if (idPostMap.containsKey(postId)) {
                        resourceList.add(idPostMap.get(postId).get(0));
                    } else {
                        // 从 es 清空 db 已物理删除的数据
                        String delete = elasticsearchRestTemplate.delete(String.valueOf(postId), PostEsDTO.class);
                        log.info("delete post {}", delete);
                    }
                });
            }
        }
        page.setRecords(resourceList);
        return page;
    }

    /**
     * 查询Post基于Mysql
     * @param post
     * @param request
     * @return
     */
    //@Override
    public PostVO getPostVO2(Post post, HttpServletRequest request) {
        PostVO postVO = PostVO.objToVo(post);
        long postId = post.getId();
        // 1. 关联查询用户信息
        Long userId = post.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        postVO.setUser(userVO);
        // 2. 已登录，获取用户点赞、收藏状态
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            // 获取点赞
            QueryWrapper<PostThumb> postThumbQueryWrapper = new QueryWrapper<>();
            postThumbQueryWrapper.in("postId", postId);
            postThumbQueryWrapper.eq("userId", loginUser.getId());
            PostThumb postThumb = postThumbMapper.selectOne(postThumbQueryWrapper);
            Boolean exists = postThumbService.hasThumb(loginUser.getId(), postId);
            postVO.setHasThumb(exists);
            // 获取收藏
            QueryWrapper<PostFavour> postFavourQueryWrapper = new QueryWrapper<>();
            postFavourQueryWrapper.in("postId", postId);
            postFavourQueryWrapper.eq("userId", loginUser.getId());
            PostFavour postFavour = postFavourMapper.selectOne(postFavourQueryWrapper);
            postVO.setHasFavour(postFavour != null);
        }
        return postVO;
    }

    /**
     * 查询Post基于Redis
     * @param post
     * @param request
     * @return
     */
    @Override
    public PostVO getPostVO(Post post, HttpServletRequest request) {
        PostVO postVO = PostVO.objToVo(post);
        long postId = post.getId();
        Date createTime = post.getCreateTime();

        // 1. 关联用户信息
        Long userId = post.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        postVO.setUser(userService.getUserVO(user));

        // 2. 登录用户：获取点赞/收藏状态
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            long now = System.currentTimeMillis();
            boolean isHotPost = false;

            // 判断是否为热帖（30天内）
            if (createTime != null) {
                long createTimeMillis = createTime.getTime();
                isHotPost = (now - createTimeMillis) <= 30L * 24 * 3600 * 1000;
            }

            boolean hasThumb = false;
            boolean hasFavour = false;

            // ========== 点赞状态：优先 Redis ==========
            if (isHotPost) {
                RMap<String, String> userThumbMap = redissonClient.getMap("thumb:" + loginUser.getId());
                String thumbJson = userThumbMap.get(String.valueOf(postId));
                if (thumbJson != null) {
                    try {
                        ThumbCacheVO cacheVO = JSONUtil.toBean(thumbJson, ThumbCacheVO.class);
                        if (cacheVO != null && cacheVO.getExpireTime() > now) {
                            hasThumb = true;
                        } else {
                            // 逻辑过期，清理缓存
                            userThumbMap.fastRemove(String.valueOf(postId));
                        }
                    } catch (Exception e) {
                        log.warn("解析点赞缓存失败, userId={}, postId={}", loginUser.getId(), postId, e);
                        userThumbMap.fastRemove(String.valueOf(postId));
                    }
                }
                // Redis 未命中，后续查 DB
            }

            // 若未命中 Redis 或是冷帖，查 DB
            if (!hasThumb) {
                hasThumb = postThumbService.hasThumb(loginUser.getId(), postId);
            }

            // ========== 收藏状态（可选：也可加 Redis 缓存）==========
            // 当前保持原逻辑（查 DB），如需优化可类似实现 favour 缓存
            hasFavour = postFavourService.hasFavour(loginUser.getId(), postId);

            postVO.setHasThumb(hasThumb);
            postVO.setHasFavour(hasFavour);
        }

        return postVO;
    }

    /**
     * 查询Post信息基于Mysql
     * @param postPage
     * @param request
     * @return
     */
    //@Override
    public Page<PostVO> getPostVO2(Page<Post> postPage, HttpServletRequest request) {
        List<Post> postList = postPage.getRecords();
        Page<PostVO> postVOPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
        if (CollUtil.isEmpty(postList)) {
            return postVOPage;
        }
        // 1. 关联查询用户信息
        Set<Long> userIdSet = postList.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 已登录，获取用户点赞、收藏状态
        Map<Long, Boolean> postIdHasThumbMap = new HashMap<>();
        Map<Long, Boolean> postIdHasFavourMap = new HashMap<>();
        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            Set<Long> postIdSet = postList.stream().map(Post::getId).collect(Collectors.toSet());
            loginUser = userService.getLoginUser(request);
            // 获取点赞
            QueryWrapper<PostThumb> postThumbQueryWrapper = new QueryWrapper<>();
            postThumbQueryWrapper.in("postId", postIdSet);
            postThumbQueryWrapper.eq("userId", loginUser.getId());
            List<PostThumb> postPostThumbList = postThumbMapper.selectList(postThumbQueryWrapper);
            postPostThumbList.forEach(postPostThumb -> postIdHasThumbMap.put(postPostThumb.getPostId(), true));
            // 获取收藏
            QueryWrapper<PostFavour> postFavourQueryWrapper = new QueryWrapper<>();
            postFavourQueryWrapper.in("postId", postIdSet);
            postFavourQueryWrapper.eq("userId", loginUser.getId());
            List<PostFavour> postFavourList = postFavourMapper.selectList(postFavourQueryWrapper);
            postFavourList.forEach(postFavour -> postIdHasFavourMap.put(postFavour.getPostId(), true));
        }
        // 填充信息
        List<PostVO> postVOList = postList.stream().map(post -> {
            PostVO postVO = PostVO.objToVo(post);
            Long userId = post.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            postVO.setUser(userService.getUserVO(user));
            postVO.setHasThumb(postIdHasThumbMap.getOrDefault(post.getId(), false));
            postVO.setHasFavour(postIdHasFavourMap.getOrDefault(post.getId(), false));
            return postVO;
        }).collect(Collectors.toList());
        postVOPage.setRecords(postVOList);
        return postVOPage;
    }

    /**
     * 查询Post信息基于Redis
     * @param postPage
     * @param request
     * @return
     */
    @Override
    public Page<PostVO> getPostVOPage(Page<Post> postPage, HttpServletRequest request) {
        List<Post> postList = postPage.getRecords();
        Page<PostVO> postVOPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
        if (CollUtil.isEmpty(postList)) {
            return postVOPage;
        }

        // 1. 关联用户信息
        Set<Long> userIdSet = postList.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIdSet)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (u1, u2) -> u1));

        // 2. 初始化状态映射
        Map<Long, Boolean> postIdHasThumbMap = new HashMap<>();
        Map<Long, Boolean> postIdHasFavourMap = new HashMap<>();

        User loginUser = userService.getLoginUserPermitNull(request);
        if (loginUser != null) {
            Set<Long> postIdSet = postList.stream().map(Post::getId).collect(Collectors.toSet());
            long now = System.currentTimeMillis();

            // ========== 批量获取点赞状态（Redisson + 冷热分离）==========
            for (Post post : postList) {
                Long postId = post.getId();
                Date createTime = post.getCreateTime();
                if (createTime == null) continue;

                long createTimeMillis = createTime.getTime();
                boolean isHotPost = (now - createTimeMillis) <= 30L * 24 * 3600 * 1000; // 30天内

                if (isHotPost) {
                    // 热帖：查 Redis
                    RMap<String, String> userThumbMap = redissonClient.getMap("thumb:" + loginUser.getId());
                    String thumbJson = userThumbMap.get(postId.toString());
                    if (thumbJson != null) {
                        try {
                            ThumbCacheVO vo = JSONUtil.toBean(thumbJson, ThumbCacheVO.class);
                            if (vo != null && vo.getExpireTime() > now) {
                                postIdHasThumbMap.put(postId, true);
                                continue; // 已命中，跳过 DB 查询
                            } else {
                                // 逻辑过期，清理
                                userThumbMap.fastRemove(postId.toString());
                            }
                        } catch (Exception e) {
                            log.warn("解析点赞缓存失败, userId={}, postId={}", loginUser.getId(), postId, e);
                            userThumbMap.fastRemove(postId.toString());
                        }
                    }
                    // Redis 未命中，后续统一查 DB（避免多次小查询）
                }
                // 冷帖 or Redis 未命中：标记需查 DB
                postIdHasThumbMap.putIfAbsent(postId, false); // 占位，后续覆盖
            }

            // ========== 批量查 DB 补全未命中项 ==========
            Set<Long> needCheckFromDb = new HashSet<>();
            for (Long postId : postIdSet) {
                if (!postIdHasThumbMap.containsKey(postId) || !postIdHasThumbMap.get(postId)) {
                    needCheckFromDb.add(postId);
                }
            }

            if (!needCheckFromDb.isEmpty()) {
                QueryWrapper<PostThumb> qw = new QueryWrapper<>();
                qw.eq("userId", loginUser.getId()).in("postId", needCheckFromDb);
                List<PostThumb> dbThumbs = postThumbMapper.selectList(qw);
                for (PostThumb thumb : dbThumbs) {
                    postIdHasThumbMap.put(thumb.getPostId(), true);
                }
            }

            // ========== 收藏状态（暂不缓存，或类似处理）==========
            // 如果收藏也需要缓存，可类似实现；此处保持原逻辑
            QueryWrapper<PostFavour> favourQw = new QueryWrapper<>();
            favourQw.eq("userId", loginUser.getId()).in("postId", postIdSet);
            List<PostFavour> postFavourList = postFavourMapper.selectList(favourQw);
            for (PostFavour favour : postFavourList) {
                postIdHasFavourMap.put(favour.getPostId(), true);
            }
        }

        // 3. 构建 VO
        List<PostVO> postVOList = postList.stream().map(post -> {
            PostVO postVO = PostVO.objToVo(post); // 假设已包含 thumbNum

            // 用户信息
            User user = userMap.get(post.getUserId());
            postVO.setUser(userService.getUserVO(user));

            // 点赞/收藏状态
            postVO.setHasThumb(postIdHasThumbMap.getOrDefault(post.getId(), false));
            postVO.setHasFavour(postIdHasFavourMap.getOrDefault(post.getId(), false));

            return postVO;
        }).collect(Collectors.toList());

        postVOPage.setRecords(postVOList);
        return postVOPage;
    }
}




