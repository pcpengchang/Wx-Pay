package com.ace.payment.controller;

import com.ace.payment.service.WxPayService;
import com.ace.payment.util.HttpUtils;
import com.ace.payment.util.WechatPay2ValidatorForRequest;
import com.ace.payment.vo.R;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// 同样的通知可能会多次发送给商户系统。商户系统必须能够正确处理重复的通知
//商户系统对于开启结果通知的内容一定要做签名验证，并校验通知的信息是否与
// 商户侧的信息一致，防止数据泄露导致出现“假通知”，造成资金损失
@CrossOrigin //跨域
@RestController
@RequestMapping("/api/wx-pay")
@Api(tags = "网站微信支付APIv3")
@Slf4j
public class WxPayController {

    @Resource
    private WxPayService wxPayService;

    @Resource
    private Verifier verifier;

    /**
     * Native下单
     *
     * @param productId
     * @return
     * @throws Exception
     */
    @ApiOperation("调用统一下单API，生成支付二维码")
    @PostMapping("/native/{productId}")
    public R nativePay(@PathVariable Long productId) throws Exception {

        log.info("发起支付请求 v3");

        //返回支付二维码连接和订单号
        Map<String, Object> map = wxPayService.nativePay(productId);

        return R.ok().setData(map);
    }

    //接受微信异步通知  商家失败或超时 微信都会不停发
    @PostMapping("/native/notify")
    public String nativeNotify(HttpServletRequest request, HttpServletResponse response) {

        //请求体转字符串
        String body = HttpUtils.readData(request);
        Gson gson = new Gson();
        Map<String, String> map = new HashMap<>();//应答对象

        //关键看response状态码
        try {
            //字符串转哈希
            Map<String, Object> bodyMap = gson.fromJson(body, HashMap.class);
            String requestId = (String) bodyMap.get("id");
            log.info("支付通知的id ===> {}", requestId);

            ////TODO:手撕验签
            WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest
                    = new WechatPay2ValidatorForRequest(verifier, requestId, body);
            if (!wechatPay2ValidatorForRequest.validate(request)) {
                log.error("回调验签失败");

                //失败应答
                response.setStatus(500);
                map.put("code", "ERROR");
                map.put("message", "通知验签失败");
                return gson.toJson(map);
            }
            log.info("回调验签成功");
            //成功应答

            //TODO:流程图中的报文解密
            //支付半成功 处理订单
            wxPayService.processOrder(bodyMap);

            response.setStatus(200);
            map.put("code", "SUCCESS");
            map.put("message", "成功");
            return gson.toJson(map);
        } catch (Exception e) {
            e.printStackTrace();
            //失败应答
            response.setStatus(500);
            map.put("code", "ERROR");
            map.put("message", "通知验签失败");
            return gson.toJson(map);
        }
    }

    /**
     * 用户取消订单
     *
     * @param orderNo
     * @return
     * @throws Exception
     */
    @ApiOperation("用户取消订单")
    @PostMapping("/cancel/{orderNo}")
    public R cancelOrder(@PathVariable String orderNo) throws IOException {

        log.info("取消订单");

        wxPayService.cancelOrder(orderNo);
        return R.ok().setMessage("订单已取消");
    }

    //商户可以通过查询订单接口主动查询订单状态，完成下一步的业务逻辑。
    // 查询订单状态可通过微信支付订单号或商户订单号两种方式查询
    ////需要调用查询接口的情况：
    //当商户后台、网络、服务器等出现异常，商户系统最终未接收到支付通知。
    //用支付接口后，返回系统错误或未知交易状态情况。
    //调用付款码支付API，返回USERPAYING的状态。
    //调用关单或撤销接口API之前，需确认支付状态。
    @ApiOperation("查询订单：测试用")
    @GetMapping("/query/{orderNo}")
    public R queryOrder(@PathVariable String orderNo) throws IOException {

        log.info("查询订单");

        String result = wxPayService.queryOrder(orderNo);
        return R.ok().setMessage("订单已查询").data("result", result);
    }

    @ApiOperation("申请退款")
    @PostMapping("/refunds/{orderNo}/{reason}")
    public R refunds(@PathVariable String orderNo, @PathVariable String reason) throws Exception {

        log.info("申请退款");
        wxPayService.refund(orderNo, reason);
        return R.ok();
    }

    //    提交退款申请后，通过调用该接口查询退款状态。退款有一定延时，建议在提交退款申请后1分钟发起查询退款状态，
    //    一般来说零钱支付的退款5分钟内到账，银行卡支付的退款1-3个工作日到账。
    @ApiOperation("查询退款：测试用")
    @GetMapping("/query-refund/{refundNo}")
    public R queryRefund(@PathVariable String refundNo) throws IOException {

        log.info("查询退款");

        String result = wxPayService.queryRefund(refundNo);
        return R.ok().setMessage("查询成功").data("result", result);
    }

    //对后台通知交互时，如果微信收到应答不是成功或超时，微信认为通知失败，微信会通过一定的策略
    // 定期重新发起通知，尽可能提高通知的成功率，但微信不保证通知最终能成功
    @ApiOperation("退款结果通知")
    @PostMapping("/refunds/notify")
    public String refundsNotify(HttpServletRequest request, HttpServletResponse response) {

        log.info("退款通知执行");
        Gson gson = new Gson();
        Map<String, String> map = new HashMap<>();//应答对象

        try {
            //处理通知参数
            String body = HttpUtils.readData(request);
            Map<String, Object> bodyMap = gson.fromJson(body, HashMap.class);
            String requestId = (String) bodyMap.get("id");
            log.info("支付通知的id ===> {}", requestId);

            //签名的验证
            WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest
                    = new WechatPay2ValidatorForRequest(verifier, requestId, body);
            if (!wechatPay2ValidatorForRequest.validate(request)) {

                log.error("通知验签失败");
                //失败应答
                response.setStatus(500);
                map.put("code", "ERROR");
                map.put("message", "通知验签失败");
                return gson.toJson(map);
            }
            log.info("通知验签成功");

            //处理退款单
            wxPayService.processRefund(bodyMap);

            //成功应答
            response.setStatus(200);
            map.put("code", "SUCCESS");
            map.put("message", "成功");
            return gson.toJson(map);

        } catch (Exception e) {
            e.printStackTrace();
            //失败应答
            response.setStatus(500);
            map.put("code", "ERROR");
            map.put("message", "失败");
            return gson.toJson(map);
        }
    }

    @ApiOperation("获取账单url：测试用")
    @GetMapping("/querybill/{billDate}/{type}")
    public R queryTradeBill(
            @PathVariable String billDate,
            @PathVariable String type) throws Exception {

        log.info("获取账单url");

        String downloadUrl = wxPayService.queryBill(billDate, type);
        return R.ok().setMessage("获取账单url成功").data("downloadUrl", downloadUrl);
    }
}
