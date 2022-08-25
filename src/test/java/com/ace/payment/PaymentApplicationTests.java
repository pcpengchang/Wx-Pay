package com.ace.payment;

import com.ace.payment.config.WxPayConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Slf4j
@SpringBootTest
class PaymentApplicationTests {

    @Autowired
    WxPayConfig wxPayConfig;

    @Autowired
    DataSource dataSource;

    @Test
    void test() {
        System.out.println(wxPayConfig.getAppid());
        //System.out.println(wxPayConfig.getPrivateKey(wxPayConfig.getPrivateKeyPath()));
        System.out.println(wxPayConfig.getVerifier());
    }

    @Test
    void test2() {
        System.out.println("数据源类型" + dataSource.getClass());
        log.info("数据源类型 ===> {}", dataSource.getClass());
    }

}
