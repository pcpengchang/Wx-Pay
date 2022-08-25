package com.ace.payment.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

public interface WxPayService {
    Map<String, Object> nativePay(Long productId) throws Exception;

    void processOrder(Map<String, Object> bodyMap) throws GeneralSecurityException;

    void cancelOrder(String orderNo) throws IOException;

    void closeOrder(String orderNo) throws IOException;

    String queryOrder(String orderNo) throws IOException;

    void checkOrderStatus(String orderNo) throws IOException;

    void refund(String orderNo, String reason) throws IOException;

    String queryRefund(String refundNo) throws IOException;

    void checkRefundStatus(String refundNo) throws IOException;

    void processRefund(Map<String, Object> bodyMap) throws GeneralSecurityException;

    String queryBill(String billDate, String type) throws IOException;
}
