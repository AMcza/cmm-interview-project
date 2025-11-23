package com.yupi.springbootinit.model.vo.cache;

import lombok.Data;

@Data
public class ThumbCacheVO {

    private Long thumbId;

    private Long expireTime;
}
