package com.ace.payment.controller;


import com.ace.payment.entity.OrderInfo;
import com.ace.payment.enums.OrderStatus;
import com.ace.payment.service.OrderInfoService;
import com.ace.payment.vo.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin //开放跨域访问
@Api(tags = "订单管理") //用在类上
@RestController
@RequestMapping("/api/order-info")
public class OrderInfoController {

    @Autowired
    private OrderInfoService orderInfoService;

    @ApiOperation("展示订单列表")
    @GetMapping("/list")
    public R list() {
        List<OrderInfo> list = orderInfoService.listOrderByCreateTimeDesc();
        return R.ok().data("list", list);
    }

    /**
     * 查询本地订单状态
     *
     * @param orderNo
     * @return
     */
    @ApiOperation("查询本地订单状态")//定时查单
    @GetMapping("/query-order-status/{orderNo}")
    public R queryOrderStatus(@PathVariable String orderNo) {
        String orderStatus = orderInfoService.getOrderStatus(orderNo);
        if (OrderStatus.SUCCESS.getType().equals(orderStatus)) {
            return R.ok().setMessage("支付成功"); //支付成功
        }
        //协商好是 101 作为支付中的状态码
        return R.ok().setCode(101).setMessage("支付中......");
    }
}
