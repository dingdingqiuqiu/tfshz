package com.tfsh.Service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tfsh.Service.IShopFavoriteService;
import com.tfsh.Service.IShopService;
import com.tfsh.dto.Result;
import com.tfsh.dto.UserDTO;
import com.tfsh.entity.Shop;
import com.tfsh.entity.ShopFavorite;
import com.tfsh.mapper.ShopFavoriteMapper;
import com.tfsh.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShopFavoriteServiceImpl extends ServiceImpl<ShopFavoriteMapper, ShopFavorite> implements IShopFavoriteService {

    @Resource
    private IShopService shopService;

    @Override
    public Result favorite(Long shopId, Boolean isFavorite) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        if (shopId == null) {
            return Result.fail("参数错误");
        }

        if (Boolean.TRUE.equals(isFavorite)) {
            Long exists = query()
                    .eq("user_id", user.getId())
                    .eq("shop_id", shopId)
                    .count();
            if (exists != null && exists > 0) {
                return Result.ok();     // 已经收藏过，幂等返回
            }
            try {
                save(new ShopFavorite()
                        .setUserId(user.getId())
                        .setShopId(shopId)
                        .setCreateTime(LocalDateTime.now()));
            } catch (DuplicateKeyException e) {
                // 并发重复收藏：唯一索引 uk_user_shop 拦下，属于幂等成功
                log.info("[favorite] 重复收藏已被唯一索引拦下 userId={} shopId={}", user.getId(), shopId);
            }
        } else {
            remove(Wrappers.<ShopFavorite>lambdaQuery()
                    .eq(ShopFavorite::getUserId, user.getId())
                    .eq(ShopFavorite::getShopId, shopId));
        }
        return Result.ok();
    }

    @Override
    public Result isFavorite(Long shopId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.ok(false);
        }
        Long count = query()
                .eq("user_id", user.getId())
                .eq("shop_id", shopId)
                .count();
        return Result.ok(count != null && count > 0);
    }

    @Override
    public Result queryMyFavorites() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        List<ShopFavorite> favorites = query()
                .eq("user_id", user.getId())
                .orderByDesc("create_time")
                .list();
        if (favorites == null || favorites.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> shopIds = favorites.stream().map(ShopFavorite::getShopId).collect(Collectors.toList());
        Map<Long, Shop> shopMap = shopService.listByIds(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, shop -> shop, (a, b) -> a, LinkedHashMap::new));

        // 按收藏顺序返回，顺便过滤掉已经下架/被删除的店铺
        List<Shop> result = new ArrayList<>(shopIds.size());
        for (Long shopId : shopIds) {
            Shop shop = shopMap.get(shopId);
            if (shop != null) {
                result.add(shop);
            }
        }
        return Result.ok(result);
    }
}
