package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.google.j2objc.annotations.ObjectiveCName;
import com.sky.annoction.AutoFill;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.DishSetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service


public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private DishSetmealMapper dishSetmealMapper;

    @Override
    @Transactional//声明式事务，因为这里涉及到两次数据库操作，所以需要声明式事务
    public void addDish(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.insert(dish);
        //添加菜品相关口味
        if (dishDTO.getFlavors() != null && !dishDTO.getFlavors().isEmpty()){
            List<DishFlavor> flavors = dishDTO.getFlavors();
            flavors.forEach(flavor -> flavor.setDishId(dish.getId()));
            dishFlavorMapper.insertBatch(flavors);
        }
    }

    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.list(dishPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }
    @Override
    @Transactional
    public void deleteByIds(List<Long> ids) {
        //根据id获取要删除的菜品，检查是否启售
        for (Long id : ids){
            Dish dish = dishMapper.getById(id);
            if (dish.getStatus() == StatusConstant.ENABLE){
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        //检查菜品是否关联套餐
        List<Long> setmealIds = dishSetmealMapper.listSetmealIdsByDishIds(ids);
        if (setmealIds != null && !setmealIds.isEmpty()){
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        //删除菜品
        dishMapper.deleteByIds(ids);
        //删除菜品相关口味
        dishFlavorMapper.deleteByDishIds(ids);
    }

    @Override
    @Transactional
    public DishVO getByIdWithFlavors(Long id) {
        DishVO dishVO = new DishVO();
        //根据id获取菜品基本信息
        Dish dish = dishMapper.getById(id);
        //获取菜品口味
        List<DishFlavor> flavors = dishFlavorMapper.getByDishId(id);
        //封装菜品信息
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(flavors);
        return dishVO;
    }
    @Override
    @Transactional
    public void updateDish(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        //更新菜品基本信息
        dishMapper.update(dish);
        //删除菜品相关口味
        List<Long> dishIds = new ArrayList<>();
        dishIds.add(dishDTO.getId());
        dishFlavorMapper.deleteByDishIds(dishIds);
        //添加菜品相关口味
        if (dishDTO.getFlavors() != null && !dishDTO.getFlavors().isEmpty()){
            List<DishFlavor> flavors = dishDTO.getFlavors();
            flavors.forEach(flavor -> flavor.setDishId(dish.getId()));
            dishFlavorMapper.insertBatch(flavors);
        }
    }
    @Override
    @Transactional
    public void updateStatus(String status, Long id) {
        //根据id获取菜品
        Dish dish = dishMapper.getById(id);
        dish.setStatus(Integer.parseInt(status));
        //如果是停售，菜品相关套餐也停售

        //更新菜品状态
        dishMapper.update(dish);
    }
    //根据分类id查询菜品列表
    @Override
    public List<Dish> list(Long categoryId) {
        return dishMapper.listByCategoryId(categoryId);
    }
    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    @Override
    @Transactional
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.listDish(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }
}
