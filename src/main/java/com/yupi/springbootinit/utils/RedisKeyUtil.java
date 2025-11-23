package com.yupi.springbootinit.utils;


import com.yupi.springbootinit.constant.ThumbConstant;

public class RedisKeyUtil {

    public static String getUserThumbKey(Long userId){
        return ThumbConstant.USER_THUMB_KEY_PREFIX+ userId;
    }

    public static String getTempThumbKey(String time){
        return ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(time);
    }
}
