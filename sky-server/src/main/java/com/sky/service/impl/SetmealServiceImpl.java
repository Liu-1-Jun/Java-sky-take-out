package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private DishMapper dishMapper;

    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("分页查询套餐列表，参数：{}", setmealPageQueryDTO);
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> setmealPage = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(setmealPage.getTotal(), setmealPage.getResult());
    }
    @Override
    @Transactional
    public void save(SetmealDTO setmealDTO) {
        log.info("新增套餐，参数：{}", setmealDTO);
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.insert(setmeal);
        //新增套餐和菜品关系
        if (setmealDTO.getSetmealDishes() != null && setmealDTO.getSetmealDishes().size() > 0){
            log.info("新增套餐和菜品关系，参数：{}", setmealDTO.getSetmealDishes());
            List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmeal.getId()));
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }
    @Override
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        //修改套餐基础信息
        log.info("修改套餐，参数：{}", setmealDTO);
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.update(setmeal);
        //删除套餐和菜品关系
        setmealDishMapper.deleteBySetmealId(setmealDTO.getId());
        //新增套餐和菜品关系
        if (setmealDTO.getSetmealDishes() != null && setmealDTO.getSetmealDishes().size() > 0){
            log.info("新增套餐和菜品关系，参数：{}", setmealDTO.getSetmealDishes());
            List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
            setmealDishes.forEach(setmealDish -> setmealDish.setSetmealId(setmeal.getId()));
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }
    @Override
    @Transactional
    public SetmealVO getById(Long id) {
        log.info("根据id查询套餐，参数：{}", id);
        //查询套餐基础信息
        Setmeal setmeal = setmealMapper.getById(id);
        //查询套餐和菜品关系
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);
        SetmealVO setmealVO = new SetmealVO();
        BeanUtils.copyProperties(setmeal, setmealVO);
        setmealVO.setSetmealDishes(setmealDishes);
        return setmealVO;
    }
    @Override
    @Transactional
    public void delete(List<Long> ids) {
        log.info("删除套餐，参数：{}", ids);
        //判断套餐是否在售
        List<Setmeal> setmeals = setmealMapper.getByIds(ids);
        for (Setmeal setmeal : setmeals) {
            if (setmeal.getStatus() == 1) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        //删除套餐
        setmealMapper.deleteByIds(ids);
        //删除套餐和菜品关系
        setmealDishMapper.deleteBySetmealIds(ids);
    }
    @Override
    @Transactional
    public void updateStatus(Integer status, Long id) {
        log.info("修改套餐状态，参数：{}", id);
        List<Long> dishIds = new ArrayList<>();
        //查看套餐相关菜品是否停售
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);
        for (SetmealDish setmealDish : setmealDishes) {
            dishIds.add(setmealDish.getDishId());
        }
        List<Dish> dishes = dishMapper.getByIds(dishIds);
        for (Dish dish : dishes) {
            if (dish.getStatus() == 0) {
                throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
            }
        }
        //修改套餐
        Setmeal setmeal = setmealMapper.getById(id);
        setmeal.setStatus(status);
        setmealMapper.update(setmeal);
    }
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        log.info("根据条件查询套餐，参数：{}", setmeal);
        return setmealMapper.list(setmeal);
    }
    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
