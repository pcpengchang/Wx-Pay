package com.ace.payment.controller;

import com.ace.payment.entity.Product;
import com.ace.payment.service.ProductService;
import com.ace.payment.vo.R;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

@CrossOrigin //开放跨域访问
@Api(tags = "商品管理") //用在类上
@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/test")
    public R test() {
        return R.ok().data("message", "hello").data("now", new Date());
    }

    @GetMapping("/list")
    public R list() {
        List<Product> list = productService.list();
        return R.ok().data("productList", list);
    }
}
