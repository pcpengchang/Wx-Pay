package com.ace.payment.service.impl;

import com.ace.payment.entity.Product;
import com.ace.payment.mapper.ProductMapper;
import com.ace.payment.service.ProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

}
