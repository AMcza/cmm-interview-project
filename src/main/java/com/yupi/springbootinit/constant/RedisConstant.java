package com.yupi.springbootinit.constant;

public interface RedisConstant {
    /**
     * 用户签到记录的Redis key前缀
     */
    String USER_SIGN_IN_REDIS_KEY_PREFIX="user:signins";

    /**
     * 获取用户签到记录的Redis key
     * @param year
     * @param userId
     * @return
     */
    static String getUserSignInRedisKey(int year,long userId){
        return String.format("%s:%d:%d",USER_SIGN_IN_REDIS_KEY_PREFIX,year,userId);
    }
}
