package com.tfsh.Service.impl;

// 新增
import com.tfsh.dto.LoginResultDTO;
import com.tfsh.utils.JwtUtils;
import io.jsonwebtoken.Claims;
// 可删除（改造后不再使用）
// import cn.hutool.core.lang.UUID;          // jti 改用 java.util.UUID，在 JwtUtils 里
// import cn.hutool.core.bean.copier.CopyOptions;  // 不再需要把用户塞进 Redis Hash

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tfsh.Service.IUserService;
import com.tfsh.dto.LoginFormDTO;
import com.tfsh.dto.Result;
import com.tfsh.dto.UserDTO;
import com.tfsh.entity.User;
import com.tfsh.mapper.UserMapper;
import com.tfsh.utils.RegexUtils;
import com.tfsh.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.tfsh.utils.RedisConstants.*;
import static com.tfsh.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到 session
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 不一致，报错
            return Result.fail("验证码错误");
        }

        // 4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }

        // // 7.保存用户信息到 redis中
        // // 7.1.随机生成token，作为登录令牌
        // String token = UUID.randomUUID().toString(true);
        // // 7.2.将User对象转为HashMap存储
        // UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
        // CopyOptions.create()
        // .setIgnoreNullValue(true)
        // .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // // 7.3.存储
        // String tokenKey = LOGIN_USER_KEY + token;
        // stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // // 7.4.设置token有效期
        // stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // // 8.返回token
        // return Result.ok(token);
        // }
        // 7.签发双 token：access 短（30min，业务请求用）+ refresh 长（7d，换发用）
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String accessToken = jwtUtils.createAccessToken(userDTO);
        String refreshToken = jwtUtils.createRefreshToken(user.getId());

        // 8.refresh token 登记进 Redis 白名单（状态最小化：只存“会话是否仍有效”这个开关）
        // key 里带上 userId 与 jti：支持多设备各自独立、支持精准 / 批量作废
        Claims refreshClaims = jwtUtils.parse(refreshToken, "refresh");
        String refreshKey = LOGIN_REFRESH_KEY + user.getId() + ":" + refreshClaims.getId();
        stringRedisTemplate.opsForValue().set(
                refreshKey, "1",
                jwtUtils.getRefreshTtl(), TimeUnit.MILLISECONDS);

        // 9.返回双 token 与用户信息
        return Result.ok(new LoginResultDTO(accessToken, refreshToken, userDTO));
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }

    @Override
    public boolean saveBatch(Collection<User> entityList, int batchSize) {
        return false;
    }

    @Override
    public boolean saveOrUpdateBatch(Collection<User> entityList, int batchSize) {
        return false;
    }

    @Override
    public boolean updateBatchById(Collection<User> entityList, int batchSize) {
        return false;
    }

    @Override
    public boolean saveOrUpdate(User entity) {
        return false;
    }

    @Override
    public User getOne(Wrapper<User> queryWrapper, boolean throwEx) {
        return null;
    }

    @Override
    public Optional<User> getOneOpt(Wrapper<User> queryWrapper, boolean throwEx) {
        return Optional.empty();
    }

    @Override
    public Map<String, Object> getMap(Wrapper<User> queryWrapper) {
        return Map.of();
    }

    @Override
    public <V> V getObj(Wrapper<User> queryWrapper, Function<? super Object, V> mapper) {
        return null;
    }

    @Override
    public boolean saveBatch(Collection<User> entityList) {
        return super.saveBatch(entityList);
    }

    @Override
    public Result refreshToken(String refreshToken) {
        // 1.验签并校验类型：过期 / 伪造 / 拿 access 冒充 → 直接拒绝
        Claims claims;
        try {
            claims = jwtUtils.parse(refreshToken, "refresh");
        } catch (Exception e) {
            return Result.fail("登录状态已失效，请重新登录");
        }
        Long userId = Long.valueOf(claims.getSubject());
        String jti = claims.getId();
        String oldKey = LOGIN_REFRESH_KEY + userId + ":" + jti;

        // 2.查 Redis 白名单：这是“可撤销”的关键一步 ——
        // 验签只证明 token 是真的，查 Redis 才证明“这个登录态还没被注销 / 还没被轮换掉”。
        String flag = stringRedisTemplate.opsForValue().get(oldKey);
        if (flag == null) {
            // 旧 refresh 再次出现只有两种可能：客户端 bug，或 token 已泄露。
            // 学习项目这里只拒绝；生产环境建议顺手删掉该用户全部 refresh，强制所有设备重登。
            return Result.fail("登录状态已失效，请重新登录");
        }

        // 3.轮换（rotation）：旧 refresh 立即作废，签发新的一对
        stringRedisTemplate.delete(oldKey);

        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String newAccess = jwtUtils.createAccessToken(userDTO);
        String newRefresh = jwtUtils.createRefreshToken(userId);
        Claims newClaims = jwtUtils.parse(newRefresh, "refresh");
        stringRedisTemplate.opsForValue().set(
                LOGIN_REFRESH_KEY + userId + ":" + newClaims.getId(),
                "1",
                jwtUtils.getRefreshTtl(), TimeUnit.MILLISECONDS);

        return Result.ok(new LoginResultDTO(newAccess, newRefresh, userDTO));
    }

    @Override
    public Result logout() {
        // 登出 = 把该用户的所有 refresh 从白名单中移除。
        // 已发出的 access 不单独撤销：它最多再活 30 分钟便会自然过期（短 TTL 兜底）。
        Long userId = UserHolder.getUser().getId();
        Set<String> keys = stringRedisTemplate.keys(LOGIN_REFRESH_KEY + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        return Result.ok();
    }
}