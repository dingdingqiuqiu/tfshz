package com.tfsh.controller;

import com.tfsh.Service.IProductService;
import com.tfsh.dto.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product")
public class ProductController {

    @Resource
    private IProductService productService;

    /** 店铺在架商品（未登录也能逛，免登录白名单见 MvcConfig） */
    @GetMapping("/of/shop/{shopId}")
    public Result queryByShop(@PathVariable("shopId") Long shopId) {
        return Result.ok(productService.queryOnShelf(shopId));
    }
}
