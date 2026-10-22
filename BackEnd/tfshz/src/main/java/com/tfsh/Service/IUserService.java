package com.tfsh.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tfsh.dto.LoginFormDTO;
import com.tfsh.dto.Result;
import com.tfsh.entity.User;
import jakarta.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

    /** 用 refresh token 换发新的双 token（旧 refresh 立即作废，即轮换） */
    Result refreshToken(String refreshToken);

    /** 登出：删除当前用户的 refresh 白名单记录 */
    Result logout();
}
