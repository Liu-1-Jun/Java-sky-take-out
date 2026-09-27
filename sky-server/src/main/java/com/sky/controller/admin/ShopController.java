package com.sky.controller.admin;


import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/shop")
@Slf4j
@Api(tags = "店铺相关接口")
public class ShopController {

    public static final String SHOP_STATUS = "shopStatus";

    @Autowired
    @Qualifier("RedisTemplate")
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
    /**
     * 设置店铺状态
     */
    @PutMapping("/{status}")
    @ApiOperation("设置店铺状态")
    public Result setShopStatus(@PathVariable Integer status) {
        log.info("设置店铺状态 {}", status==1 ? "营业" : "打样");
        redisTemplate.opsForValue().set(SHOP_STATUS, status);
        return Result.success();
    }
}
