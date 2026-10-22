package com.tfsh.controller;

import com.tfsh.Service.IShopFavoriteService;
import com.tfsh.dto.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺收藏。全部接口都需要登录（未登录由 LoginInterceptor 直接 401）。
 */
@RestController
@RequestMapping("/shop-favorite")
public class ShopFavoriteController {

    @Resource
    private IShopFavoriteService shopFavoriteService;

    /** 收藏 / 取消收藏：isFavorite=true 收藏，false 取消 */
    @PutMapping("/{shopId}/{isFavorite}")
    public Result favorite(@PathVariable("shopId") Long shopId, @PathVariable("isFavorite") Boolean isFavorite) {
        return shopFavoriteService.favorite(shopId, isFavorite);
    }

    /** 当前用户是否已收藏该店 */
    @GetMapping("/or/not/{shopId}")
    public Result isFavorite(@PathVariable("shopId") Long shopId) {
        return shopFavoriteService.isFavorite(shopId);
    }

    /** 我收藏的店铺（“我的 → 收藏”列表） */
    @GetMapping("/list")
    public Result queryMyFavorites() {
        return shopFavoriteService.queryMyFavorites();
    }
}
