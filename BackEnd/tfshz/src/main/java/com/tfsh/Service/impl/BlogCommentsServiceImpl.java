package com.tfsh.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tfsh.Service.IBlogCommentsService;
import com.tfsh.entity.BlogComments;
import com.tfsh.mapper.BlogCommentsMapper;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
