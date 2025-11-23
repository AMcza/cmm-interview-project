package com.yupi.springbootinit.constant;

public interface ThumbConstant {
    //用户点赞缓存建前缀
    String USER_THUMB_KEY_PREFIX="thumb:";
    //临时点赞数据存储键前缀
    String TEMP_THUMB_KEY_PREFIX="temp:thumb:%s";
    //帖子点赞数增量缓存键前缀
    String INCREMENT_THUMB_KEY_PREFIX="thumb:increment";

    //布隆过滤器键
    String BLOOM_FILTER_KEY="bloom:postThumb";

}
