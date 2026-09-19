package com.tfsh.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tfsh.Service.IShopTypeService;
import com.tfsh.entity.ShopType;
import com.tfsh.mapper.ShopTypeMapper;
import org.springframework.stereotype.Service;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

}
