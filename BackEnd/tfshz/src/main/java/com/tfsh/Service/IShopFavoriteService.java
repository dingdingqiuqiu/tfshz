package com.tfsh.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tfsh.dto.Result;
import com.tfsh.entity.ShopFavorite;

public interface IShopFavoriteService extends IService<ShopFavorite> {

    /** 收藏 / 取消收藏（幂等） */
    Result favorite(Long shopId, Boolean isFavorite);

    /** 当前用户是否收藏了这家店 */
    Result isFavorite(Long shopId);

    /** 我收藏的店铺列表（带店铺信息，按收藏时间倒序） */
    Result queryMyFavorites();
}
