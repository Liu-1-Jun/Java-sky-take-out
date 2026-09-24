package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //md5加密密码
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        //密码比对
        // TODO 后期需要进行md5加密，然后再进行比对
        if (!password.equals(employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }
    /**
     * 新增员工
     *
     */
    public void addEmployee(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        //将DTO中的数据复制到Employee实体类中
        BeanUtils.copyProperties(employeeDTO, employee);

        //设置默认密码且加密
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));
        //设置创建时间和更新时间
//        employee.setCreateTime(LocalDateTime.now());
//        employee.setUpdateTime(LocalDateTime.now());
        //设置状态
        employee.setStatus(StatusConstant.ENABLE);
        //获取当前用户id
        long currentId = BaseContext.getCurrentId();
//        employee.setCreateUser(currentId);
//        employee.setUpdateUser(currentId);

        //设置创建人和更新人
//        employee.setCreateUser(currentId);
//        employee.setUpdateUser(currentId);

        employeeMapper.insertEmployee(employee);
    }

    /**
     * 员工分页查询
     *
     */
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());
        List<Employee> employeeList = employeeMapper.list(employeePageQueryDTO);
        Page<Employee> pageInfo = (Page<Employee>) employeeList;
        return new PageResult(pageInfo.getTotal(), pageInfo.getResult());
    }

    /**
     * 启用禁用员工
     *
     */
    public void updateStatus(Integer status, Long id) {
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();
        //设置更新人
//        employee.setUpdateUser(BaseContext.getCurrentId());
//        //设置更新时间
//        employee.setUpdateTime(LocalDateTime.now());
        employeeMapper.update(employee);
    }
    /**
     * 根据id查询员工
     *
     */
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        employee.setPassword("******");
        return employee;
    }
    /**
     * 修改员工
     *
     */
    public void updateEmployee(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
//        employee.setUpdateTime(LocalDateTime.now());
//        employee.setUpdateUser(BaseContext.getCurrentId());
        log.info("修改前员工信息：{}", employee);
        employeeMapper.update(employee);
    }
}
