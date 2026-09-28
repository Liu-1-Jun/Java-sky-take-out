package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {

    void addDish(DishDTO dishDTO);
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);
    void deleteByIds(List<Long> ids);
    DishVO getByIdWithFlavors(Long id);
    void updateDish(DishDTO dishDTO);
    void updateStatus(String status, Long id);
    List<Dish> list(Long categoryId);
    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    List<DishVO> listWithFlavor(Dish dish);
}
