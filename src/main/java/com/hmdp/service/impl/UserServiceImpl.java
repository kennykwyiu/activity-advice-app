package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.ConstantEnum;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.entity.ConstantEnum.*;
import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 * service implementation class
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // validate the phone no.
        if (RegexUtils.isPhoneInvalid(phone)) {
            // if not valid, return failure msg
            return Result.fail("not valid phone no.");
        }

        // if valid, generate code
        String code = RandomUtil.randomNumbers(6);
        // save the code in the session -> redis // set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // send the code
        log.debug("send the message code successfully, code: {}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //  validate phone no.
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // if not valid, return failure msg
            return Result.fail("not valid phone no.");
        }
        // validate verify code
        // change to redis
//        Object cacheCode = session.getAttribute(VERIFY_CODE);
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // if not valid, return failure
            return Result.fail("wrong verify code");
        }

        // if valid, query user base on phone no.
            User user = query().eq(PHONE, phone).one();

        // check user isExist or not
        if (user == null) {
            // if not, create new user and save
           user = createUserWithPhone(phone);
        }
        // save user info into session
        // change to save into Redis
        // generate token for uid
        String token = UUID.randomUUID().toString(true);
        // mapping User to Hash
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // save into Redis
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash(). putAll(tokenKey, userMap);
        // set the token expired time
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // return token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //create user
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;

    }
}
