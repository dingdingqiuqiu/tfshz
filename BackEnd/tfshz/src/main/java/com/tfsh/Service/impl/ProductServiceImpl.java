package com.tfsh.Service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tfsh.Service.IProductService;
import com.tfsh.entity.Product;
import com.tfsh.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements IProductService {

    @Override
    public List<Product> queryOnShelf(Long shopId) {
        if (shopId == null) {
            return Collections.emptyList();
        }
        return list(Wrappers.<Product>lambdaQuery()
                .eq(Product::getShopId, shopId)
                .eq(Product::getStatus, 1)
                .orderByAsc(Product::getSort)
                .orderByAsc(Product::getId));
    }
}
