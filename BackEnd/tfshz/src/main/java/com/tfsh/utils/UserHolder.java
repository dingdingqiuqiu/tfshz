package com.tfsh.utils;

import com.tfsh.dto.UserDTO;

public class UserHolder {
    // 通过ThreadLocal操作Thread对象中的ThreadLocalMap
    // tl作为键,存进去的UserDTO作为值
    // 由于是Map,所以可以通过不同的tl区分不同类型的数据
    // 比如我们在另一个BlogHolder里可以定义自己的tl
    // 他自己的tl又可以放Blog类型的数据
    // Thread.currentThread().getThreadLocalMap().put(key=tl, value=UserDTO);
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();
    public static void setUser(UserDTO user) {
        tl.set(user);
    }
    public static UserDTO getUser() {
        return tl.get();
    }
    public static void remove() {
        tl.remove();
    }
}
