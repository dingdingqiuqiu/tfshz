package com.tfsh.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tfsh.Service.IUserInfoService;
import com.tfsh.entity.UserInfo;
import com.tfsh.mapper.UserInfoMapper;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
