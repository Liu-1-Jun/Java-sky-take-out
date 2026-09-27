package com.sky.controller.user;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController("userShopController")//标识这是一个控制器类，并指定控制器的名称
@RequestMapping("/user/shop")
@Api(tags = "店铺相关接口")
public class ShopController {
    public static final String SHOP_STATUS = "shopStatus";

    @Autowired
    @Qualifier("StringRedisTemplate")
    private RedisTemplate redisTemplate;
    /**
     * 查询店铺状态
     */
    @GetMapping("/status")
    @ApiOperation("查询店铺状态")
    public Result<Integer> getShopStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS);
        log.info("店铺状态 {}", status == 1 ? "营业" : "打样");
        return Result.success(status);
    }
}
