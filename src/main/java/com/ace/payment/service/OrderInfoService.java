package com.ace.payment.service;

import com.ace.payment.entity.OrderInfo;
import com.ace.payment.enums.OrderStatus;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface OrderInfoService extends IService<OrderInfo> {
    //根据商品生成订单
    OrderInfo createOrderByProductId(Long productId);

    //OrderInfo createOrderByProductId(Long productId);

    //根据商品号生成相应二维码
    void saveCodeUrl(String orderNo, String CodeUrl);

    //查询订单列表
    List<OrderInfo> listOrderByCreateTimeDesc();

    //更新订单状态
    void updateStatusByOrderNo(String out_trade_no, OrderStatus orderStatus);

    String getOrderStatus(String out_trade_no);

    List<OrderInfo> getNoPayOrderByDuration(int minutes);

    OrderInfo getOrderByOrderNo(String orderNo);

    //退款

}
