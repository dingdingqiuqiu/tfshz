package com.tfsh.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tfsh.entity.Product;

import java.util.List;

public interface IProductService extends IService<Product> {

    /** 某店铺在架商品 */
    List<Product> queryOnShelf(Long shopId);
}
