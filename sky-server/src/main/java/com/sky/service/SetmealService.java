package com.sky.service;

import com.github.pagehelper.Page;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;

import java.util.List;

public interface SetmealService {
    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);
    void save(SetmealDTO setmealDTO);
    void update(SetmealDTO setmealDTO);
    SetmealVO getById(Long id);
    void delete(List<Long> ids);
    void updateStatus(Integer status, Long id);
    List<Setmeal> list(Setmeal setmeal);
    List<DishItemVO> getDishItemById(Long id);
}
