package com.ace.payment.service.impl;

import com.ace.payment.config.WxPayConfig;
import com.ace.payment.entity.OrderInfo;
import com.ace.payment.entity.RefundInfo;
import com.ace.payment.enums.OrderStatus;
import com.ace.payment.enums.wxpay.WxApiType;
import com.ace.payment.enums.wxpay.WxNotifyType;
import com.ace.payment.enums.wxpay.WxRefundStatus;
import com.ace.payment.enums.wxpay.WxTradeState;
import com.ace.payment.service.OrderInfoService;
import com.ace.payment.service.PaymentInfoService;
import com.ace.payment.service.RefundInfoService;
import com.ace.payment.service.WxPayService;
import com.ace.payment.util.OrderNoUtils;
import com.google.gson.Gson;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class WxPayServiceImpl implements WxPayService {

    @Resource
    private WxPayConfig wxPayConfig;

    //有两个CloseableHttpClient  通过@Bean 加 name区分  @Resource通过名字来找
    @Resource
    private CloseableHttpClient wxPayClient;

    @Resource
    private CloseableHttpClient wxPayNoSignClient; //无需应答签名

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private PaymentInfoService paymentInfoService;

    @Resource
    private RefundInfoService refundsInfoService;

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 创建订单，调用Native支付接口
     *
     * @param productId
     * @return code_url 给前端生成二维码  和 订单号
     * @throws Exception
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Map<String, Object> nativePay(Long productId) throws Exception {

        log.info("生成订单");

        //生成订单  TODO：二维码缓存到 redis 中  先查再生成
        OrderInfo orderInfo = orderInfoService.createOrderByProductId(productId);

        //TODO：二维码缓存到 redis 中
        String codeUrl = orderInfo.getCodeUrl();

        //非空  or  null
        if (orderInfo != null && !StringUtils.isEmpty(codeUrl)) {
            log.info("订单已存在，二维码已保存");
            //返回二维码
            Map<String, Object> map = new HashMap<>();

            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderNo());
            return map;
        }

        log.info("调用统一下单API");

        //调用统一下单API
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(WxApiType.NATIVE_PAY.getType()));

        // 请求body参数
        Gson gson = new Gson();
        Map paramsMap = new HashMap();
        paramsMap.put("appid", wxPayConfig.getAppid());
        paramsMap.put("mchid", wxPayConfig.getMchId());
        paramsMap.put("description", orderInfo.getTitle());
        paramsMap.put("out_trade_no", orderInfo.getOrderNo());

        // 通知地址
        paramsMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.NATIVE_NOTIFY.getType()));

        Map amountMap = new HashMap();
        amountMap.put("total", orderInfo.getTotalFee());
        amountMap.put("currency", "CNY");

        paramsMap.put("amount", amountMap);

        //将参数转换成json字符串
        String jsonParams = gson.toJson(paramsMap);
        log.info("请求参数 ===> {}" + jsonParams);

        StringEntity entity = new StringEntity(jsonParams, "utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");

        //完成签名并执行请求
        //超级封装  商户完成签名和验签
        CloseableHttpResponse response = wxPayClient.execute(httpPost);
        //CloseableHttpResponse response = null;

        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());//响应体
            int statusCode = response.getStatusLine().getStatusCode();//响应状态码
            if (statusCode == 200) { //处理成功
                log.info("成功, 返回结果 = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("成功");
            } else {
                log.info("Native下单失败,响应码 = " + statusCode + ",返回结果 = " + bodyAsString);
                throw new IOException("request failed");
            }

            //响应结果  string 转 hashmap
            Map<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);
            //二维码
            codeUrl = resultMap.get("code_url");

            //根据订单号 再保存下二维码
            String orderNo = orderInfo.getOrderNo();
            orderInfoService.saveCodeUrl(orderNo, codeUrl);

            //返回二维码
            Map<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderNo());

            return map;

        } finally {
            response.close();
        }
    }

    //进一步  通知并发问题  假如两个通知同时到达这里  需要加锁
    //在对业务数据进行状态检查和处理之前，要采用数据锁进行并发控制，
    // 以避免函数重入造成的数据混乱。
    @Override
    public void processOrder(Map<String, Object> bodyMap) throws GeneralSecurityException {

        log.info("处理订单");


        //扫完码后 两件事情 一是做验签，这部分微信没有提供SDK，需要我们手动改写
        //二是解密微信返回来的信息，信息包含订单号等  我们根据订单号往数据库里修改
        String plainText = decryptFromResource(bodyMap);

        //字符串转map
        Gson gson = new Gson();
        Map<String, Object> plainTextMap = gson.fromJson(plainText, HashMap.class);
        String out_trade_no = (String) plainTextMap.get("out_trade_no");

        //处理重复的微信通知，比如超时的时候微信会不断重传
        //接口调用的幂等性：无论接口被调用多少次，产生的结果是一致的。
        //尝试获取锁：
        // 成功获取则立即返回true，获取失败则立即返回false。不必一直等待锁的释放
        // synchronize未成功获取则一直等待 所以不使用
        if (lock.tryLock()) {
            try {
                String orderStatus = orderInfoService.getOrderStatus(out_trade_no);
                if (!OrderStatus.NOTPAY.getType().equals(orderStatus)) {
                    log.info("订单超时，微信重复发送");
                    return;
                }

                //更新订单状态
                orderInfoService.updateStatusByOrderNo(out_trade_no, OrderStatus.SUCCESS);

                //保存到订单数据库中
                //TODO:形参改为Map<String, Object> plainTextMap
                paymentInfoService.createPaymentInfo(plainText);
            } finally {
                //要主动释放锁
                lock.unlock();
            }
        }
    }

    //取消订单
    @Override
    public void cancelOrder(String orderNo) throws IOException {
        this.closeOrder(orderNo);
        orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.CANCEL);
    }

    //有两个地方会调用这个关单API 一是用户在网页主动点击取消
    //二是用户超时未支付  系统自动轮询关闭订单
    @Override
    public void closeOrder(String orderNo) throws IOException {
        log.info("调用关单API");

        //format 填充 %s 占位符
        String url = String.format(WxApiType.CLOSE_ORDER_BY_NO.getType(), orderNo);
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(url));

        // 请求body参数
        Gson gson = new Gson();
        Map<String, String> paramsMap = new HashMap<>();
        paramsMap.put("mchid", wxPayConfig.getMchId());

        //将参数转换成json字符串
        String jsonParams = gson.toJson(paramsMap);
        log.info("请求参数 ===> {}" + jsonParams);

        StringEntity entity = new StringEntity(jsonParams, "utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");

        CloseableHttpResponse response = wxPayClient.execute(httpPost);

        //文档中说明无数据  只有状态码
        try {
            int statusCode = response.getStatusLine().getStatusCode();//响应状态码
            if (statusCode == 200) { //处理成功
                log.info("成功200");
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("成功204");
            } else {
                log.info("Native下单失败,响应码 = " + statusCode);
                throw new IOException("request failed");
            }
        } finally {
            response.close();
        }
    }

    @Override
    public String queryOrder(String orderNo) throws IOException {
        log.info("调用查单API");
        String url = String.format(WxApiType.ORDER_QUERY_BY_NO.getType(), orderNo);
        url = wxPayConfig.getDomain().concat(url).concat("?mchid=").concat(wxPayConfig.getMchId());

        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpGet);

        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());//响应体
            int statusCode = response.getStatusLine().getStatusCode();//响应状态码
            if (statusCode == 200) { //处理成功
                log.info("成功, 返回结果 = " + bodyAsString);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("成功");
            } else {
                log.info("查单接口调用,响应码 = " + statusCode + ",返回结果 = " + bodyAsString);
                throw new IOException("request failed");
            }

            return bodyAsString;

        } finally {
            response.close();
        }
    }

    @Override
    public void checkOrderStatus(String orderNo) throws IOException {
        log.warn("超时未支付，根据订单号核实订单状态 ===> {}", orderNo);

        //调用微信支付查单接口
        String result = this.queryOrder(orderNo);

        Gson gson = new Gson();
        Map<String, String> resultMap = gson.fromJson(result, HashMap.class);

        //获取微信支付端的订单状态
        String tradeState = resultMap.get("trade_state");

        //判断订单状态
        if (WxTradeState.SUCCESS.getType().equals(tradeState)) {

            log.warn("核实订单已支付 ===> {}", orderNo);

            //如果确认订单已支付则更新本地订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.SUCCESS);
            //记录支付日志
            paymentInfoService.createPaymentInfo(result);
        }

        if (WxTradeState.NOTPAY.getType().equals(tradeState)) {
            log.warn("核实订单未支付 ===> {}", orderNo);

            //如果订单未支付，则调用关单接口
            this.closeOrder(orderNo);

            //更新本地订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.CLOSED);
        }

    }

    @Override
    public void refund(String orderNo, String reason) throws IOException {
        log.info("创建退款单记录");
        //根据订单编号创建退款单
        RefundInfo refundsInfo = refundsInfoService.createRefundByOrderNo(orderNo, reason);

        log.info("调用退款API");

        //调用统一下单API
        String url = wxPayConfig.getDomain().concat(WxApiType.DOMESTIC_REFUNDS.getType());
        HttpPost httpPost = new HttpPost(url);

        // 请求body参数
        Gson gson = new Gson();
        Map paramsMap = new HashMap();
        paramsMap.put("out_trade_no", orderNo);//订单编号
        paramsMap.put("out_refund_no", refundsInfo.getRefundNo());//退款单编号
        paramsMap.put("reason", reason);//退款原因
        paramsMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.REFUND_NOTIFY.getType()));//退款通知地址

        Map amountMap = new HashMap();
        amountMap.put("refund", refundsInfo.getRefund());//退款金额
        amountMap.put("total", refundsInfo.getTotalFee());//原订单金额
        amountMap.put("currency", "CNY");//退款币种
        paramsMap.put("amount", amountMap);

        //将参数转换成json字符串
        String jsonParams = gson.toJson(paramsMap);
        log.info("请求参数 ===> {}" + jsonParams);

        StringEntity entity = new StringEntity(jsonParams, "utf-8");
        entity.setContentType("application/json");//设置请求报文格式
        httpPost.setEntity(entity);//将请求报文放入请求对象
        httpPost.setHeader("Accept", "application/json");//设置响应报文格式

        //完成签名并执行请求，并完成验签
        CloseableHttpResponse response = wxPayClient.execute(httpPost);

        try {

            //解析响应结果
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 退款返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("退款异常, 响应码 = " + statusCode + ", 退款返回结果 = " + bodyAsString);
            }
            //更新订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_PROCESSING);

            //更新退款单
            refundsInfoService.updateRefund(bodyAsString);

        } finally {
            response.close();
        }
    }

    @Override
    public String queryRefund(String refundNo) throws IOException {
        log.info("查询退款接口调用 ===> {}", refundNo);

        String url = String.format(WxApiType.DOMESTIC_REFUNDS_QUERY.getType(), refundNo);
        url = wxPayConfig.getDomain().concat(url);

        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpGet);

        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 查询退款返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("查询退款异常, 响应码 = " + statusCode + ", 查询退款返回结果 = " + bodyAsString);
            }

            return bodyAsString;

        } finally {
            response.close();
        }
    }

    @Override
    public void checkRefundStatus(String refundNo) throws IOException {
        log.warn("根据退款单号核实退款单状态 ===> {}", refundNo);

        //调用查询退款单接口
        String result = this.queryRefund(refundNo);

        //组装json请求体字符串
        Gson gson = new Gson();
        Map<String, String> resultMap = gson.fromJson(result, HashMap.class);
        //获取微信支付端退款状态
        String status = resultMap.get("status");
        String orderNo = resultMap.get("out_trade_no");
        if (WxRefundStatus.SUCCESS.getType().equals(status)) {
            log.warn("核实订单已退款成功 ===> {}", refundNo);
            //如果确认退款成功，则更新订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS);

            //更新退款单
            refundsInfoService.updateRefund(result);
        }

        if (WxRefundStatus.ABNORMAL.getType().equals(status)) {
            log.warn("核实订单退款异常  ===> {}", refundNo);
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_ABNORMAL);

            //更新退款单
            refundsInfoService.updateRefund(result);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processRefund(Map<String, Object> bodyMap) throws GeneralSecurityException {
        //解密报文
        String plainText = decryptFromResource(bodyMap);

        //将明文转换成map
        Gson gson = new Gson();
        HashMap plainTextMap = gson.fromJson(plainText, HashMap.class);
        String orderNo = (String) plainTextMap.get("out_trade_no");

        if (lock.tryLock()) {
            try {
                String orderStatus = orderInfoService.getOrderStatus(orderNo);
                if (!OrderStatus.REFUND_PROCESSING.getType().equals(orderStatus)) {
                    return;
                }
                //更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS);
                //更新退款单
                refundsInfoService.updateRefund(plainText);
            } finally {
                //要主动释放锁
                lock.unlock();
            }
        }
    }

    @Override
    public String queryBill(String billDate, String type) throws IOException {
        log.warn("申请账单接口调用 {}", billDate);

        String url = "";
        if ("tradebill".equals(type)) {
            url = WxApiType.TRADE_BILLS.getType();
        } else if ("fundflowbill".equals(type)) {
            url = WxApiType.FUND_FLOW_BILLS.getType();
        } else {
            throw new RuntimeException("不支持的账单类型");
        }

        url = wxPayConfig.getDomain().concat(url).concat("?bill_date=").concat(billDate);

        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Accept", "application/json");

        //使用wxPayClient发送请求得到响应
        CloseableHttpResponse response = wxPayClient.execute(httpGet);

        try {

            String bodyAsString = EntityUtils.toString(response.getEntity());

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 申请账单返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("申请账单异常, 响应码 = " + statusCode + ", 申请账单返回结果 = " + bodyAsString);
            }

            //获取账单下载地址
            Gson gson = new Gson();
            Map<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);
            return resultMap.get("download_url");

        } finally {
            response.close();
        }
    }

    //对称解密
    private String decryptFromResource(Map<String, Object> bodyMap) throws GeneralSecurityException {
        log.info("开始解密");

        //通知数据
        Map<String, String> resourceMap = (Map) bodyMap.get("resource");

        //aes 解密形参要啥我就提取啥

        String associated_data = resourceMap.get("associated_data");
        String nonce = resourceMap.get("nonce");
        String ciphertext = resourceMap.get("ciphertext");

        AesUtil aesUtil = new AesUtil(wxPayConfig.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        String plainText = aesUtil.decryptToString(associated_data.getBytes(StandardCharsets.UTF_8),
                nonce.getBytes(StandardCharsets.UTF_8),
                ciphertext);
        log.info("明文 ===> {}", plainText);
        return plainText;
    }
}
