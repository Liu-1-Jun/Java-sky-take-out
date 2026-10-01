package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service

public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 提交订单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submit( OrdersSubmitDTO ordersSubmitDTO) {
        //判断提交的订单合法性
        //判断提交的地址是否为空
        Long addressBookId = ordersSubmitDTO.getAddressBookId();
        Long userId = BaseContext.getCurrentId();
        AddressBook addressBook = addressBookMapper.getById(addressBookId);
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);//用户地址为空，不能下单
        }
        //判断提交购物车是否为空
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);//购物车为空，不能下单
        }
        //完善订单基本信息并插入
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setUserId(userId);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setOrderTime(LocalDateTime.now());
        orders.setConsignee(addressBook.getConsignee());
        orders.setPhone(addressBook.getPhone());
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setPayStatus(Orders.UN_PAID);

        orderMapper.insert(orders);
        //插入订单明细
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList){
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        //包装VO返回数据
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();


        //来单提醒
        HashMap map = new HashMap();
        map.put("type",1);
        map.put("orderId",orders.getId());
        map.put("content","订单号："+orders.getNumber());
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
        return orderSubmitVO;
    }

    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("订单分页查询，参数：{}", ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        //查询订单详情
        if (page != null && page.getResult() != null && page.getResult().size() > 0){
            for (Orders orders : page.getResult()){
                Long orderId = orders.getId();
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
                orderVO.setOrderDetailList(orderDetailList);
                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }
    @Override
    public OrderVO orderDetail(Long id) {
        log.info("订单详情，id：{}", id);
        OrderVO orderVO = new OrderVO();
        Orders orders = orderMapper.getById(id);
        BeanUtils.copyProperties(orders, orderVO);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }
    @Override
    public void cancel(Long id) {
        log.info("订单取消，id：{}", id);
        Orders orders = new Orders();
        orders.setId(id);
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }
    @Override
    public void repetition(Long id) {
        log.info("再来一单，id：{}", id);
        Orders orders = orderMapper.getById(id);
        Orders newOrders = new Orders();
        BeanUtils.copyProperties(orders, newOrders);
        newOrders.setCancelReason(null);
        newOrders.setCancelTime(null);
        newOrders.setDeliveryTime(null);
        newOrders.setEstimatedDeliveryTime(null);
        newOrders.setOrderTime(LocalDateTime.now());
        newOrders.setStatus(Orders.PENDING_PAYMENT);
        newOrders.setRejectionReason(null);
        newOrders.setCheckoutTime(null);
        newOrders.setNumber(String.valueOf(System.currentTimeMillis()));
        log.info("再来一单，新订单：{}", newOrders);
        orderMapper.insert(newOrders);
    }
}
