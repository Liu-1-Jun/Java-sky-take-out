package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/shoppingCart")
@Api(tags = "购物车相关接口")
@Slf4j
public class ShoppingCartController {
    @Autowired
    private ShoppingCartService shoppingCartService;
    @PostMapping("/add")
    @ApiOperation("添加购物车")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("购物车添加");
        shoppingCartService.add(shoppingCartDTO);
        return Result.success();
    }
    @GetMapping("/list")
    @ApiOperation("查看购物车")
    public Result<List<ShoppingCart>> list() {
        log.info("查看购物车");
        List<ShoppingCart> list = shoppingCartService.showShoppingCart(BaseContext.getCurrentId());
        return Result.success(list);
    }
    @PostMapping("/sub")
    @ApiOperation("购物车减量")
    public Result sub(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("购物车减量");
        shoppingCartService.sub(shoppingCartDTO);
        return Result.success();
    }
    @DeleteMapping("/clean")
    @ApiOperation("清空购物车")
    public Result clean() {
        log.info("清空购物车");
        shoppingCartService.clean(BaseContext.getCurrentId());
        return Result.success();
    }
}
