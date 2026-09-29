package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private DishMapper dishMapper;

    @Override
    @Transactional
    public void add(ShoppingCartDTO shoppingCartDTO) {
        //判断购物车中是否已经存在该菜品
        log.info("购物车添加：{}", shoppingCartDTO);
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        //如果存在，则数量加一
        if (shoppingCartList != null && shoppingCartList.size() > 0) {
            ShoppingCart existShoppingCart = shoppingCartList.get(0);
            existShoppingCart.setNumber(existShoppingCart.getNumber() + 1);
            shoppingCartMapper.updateNumber(existShoppingCart);
        }
        //如果不存在，则添加到购物车
        else {
            //判断添加的是菜品还是套餐
            if (shoppingCartDTO.getDishId() != null) {
                Dish dish = dishMapper.getById(shoppingCartDTO.getDishId());
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
                shoppingCart.setNumber(1);
                shoppingCart.setCreateTime(LocalDateTime.now());
                shoppingCartMapper.add(shoppingCart);
            } else {
                Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
                shoppingCart.setNumber(1);
                shoppingCart.setCreateTime(LocalDateTime.now());
                shoppingCartMapper.add(shoppingCart);
            }
        }
    }
    @Override
    public List<ShoppingCart> showShoppingCart(Long userId) {
        log.info("查询购物车：{}", userId);
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        return shoppingCartMapper.list(shoppingCart);
    }
    @Override
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        log.info("购物车减量：{}", shoppingCartDTO);
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        shoppingCartMapper.delete(shoppingCart);
    }
    @Override
    public void clean(Long userId) {
        log.info("购物车清空：{}", userId);
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        shoppingCartMapper.delete(shoppingCart);
    }
}
