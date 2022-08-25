package com.ace.payment.service;

import com.ace.payment.entity.RefundInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface RefundInfoService extends IService<RefundInfo> {
    RefundInfo createRefundByOrderNo(String orderNo, String reason);

    List<RefundInfo> getNoRefundOrderByDuration(int minutes);

    void updateRefund(String content);
}
