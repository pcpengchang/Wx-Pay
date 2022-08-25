package com.ace.payment.service.impl;

import com.ace.payment.entity.OrderInfo;
import com.ace.payment.entity.Product;
import com.ace.payment.enums.OrderStatus;
import com.ace.payment.mapper.OrderInfoMapper;
import com.ace.payment.mapper.ProductMapper;
import com.ace.payment.service.OrderInfoService;
import com.ace.payment.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Resource
    private ProductMapper productMapper;

    @Override
    public OrderInfo createOrderByProductId(Long productId) {
        //思路：微信支付的接口实现要求要订单号 订单金额等 驱动我要写订单类
        //订单类 驱动我要写商品类 因为一个订单可能有不同种类的商品
        // 有什么商品呢？我已经提前写到了数据库 订单类直接发请求 在商品类数据库里
        // 找商品信息 获取到商品名 价格

        //业务场景：防止用户不停地按下单键 进而不断在数据库里增加信息
        //所以先查找已存在 但未支付的订单
        OrderInfo orderInfo = this.getNoPayOrderByProductId(productId);
        if (orderInfo != null) {
            return orderInfo;
        }

        //如果是全新的订单
        //获取商品信息
        Product product = productMapper.selectById(productId);

        //生成订单
        orderInfo = new OrderInfo();
        orderInfo.setTitle(product.getTitle());
        //用随机数模拟生成订单号
        orderInfo.setOrderNo(OrderNoUtils.getOrderNo()); //订单号
        orderInfo.setProductId(productId);
        orderInfo.setTotalFee(product.getPrice()); //分

        //尚未支付
        orderInfo.setOrderStatus(OrderStatus.NOTPAY.getType());

        //即orderinfomapper
        baseMapper.insert(orderInfo);
        return orderInfo;
    }

    @Override
    public void saveCodeUrl(String orderNo, String CodeUrl) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_no", orderNo);

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCodeUrl(CodeUrl);

        baseMapper.update(orderInfo, queryWrapper);
    }

    @Override
    public List<OrderInfo> listOrderByCreateTimeDesc() {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        //根据时间倒序
        queryWrapper.orderByDesc("create_time");
        List<OrderInfo> list = baseMapper.selectList(queryWrapper);

        return list;
    }

    @Override
    public void updateStatusByOrderNo(String out_trade_no, OrderStatus orderStatus) {
        log.info("更新订单状态 ===> {}", orderStatus.getType());
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_no", out_trade_no);

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderStatus(orderStatus.getType());

        baseMapper.update(orderInfo, queryWrapper);
    }

    @Override
    public String getOrderStatus(String out_trade_no) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        //plus  里的查询  eq表示是否相等
        queryWrapper.eq("order_no", out_trade_no);
        OrderInfo orderInfo = baseMapper.selectOne(queryWrapper);
        //防止空指针异常
        if (orderInfo == null) {
            return null;
        }
        return orderInfo.getOrderStatus();
    }

    @Override
    public List<OrderInfo> getNoPayOrderByDuration(int minutes) {
        //minutes分钟之前的时间
        Instant instant = Instant.now().minus(Duration.ofMinutes(minutes));

        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_status", OrderStatus.NOTPAY.getType());
        queryWrapper.le("create_time", instant);

        List<OrderInfo> orderInfoList = baseMapper.selectList(queryWrapper);

        return orderInfoList;
    }

    @Override
    public OrderInfo getOrderByOrderNo(String orderNo) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_no", orderNo);
        OrderInfo orderInfo = baseMapper.selectOne(queryWrapper);

        return orderInfo;
    }

    private OrderInfo getNoPayOrderByProductId(Long productId) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        //plus  里的查询  eq表示是否相等
        queryWrapper.eq("product_id", productId);
        queryWrapper.eq("order_status", OrderStatus.NOTPAY.getType());
//        queryWrapper.eq("user_id", userId);
        OrderInfo orderInfo = baseMapper.selectOne(queryWrapper);
        return orderInfo;
    }
}
