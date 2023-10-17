package com.hmdp.utils.interceptor;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import com.hmdp.dto.UserDTO;
import com.hmdp.dto.factory.UserFactory;
import com.hmdp.entity.User;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER;

public class LoginInterceptor implements HandlerInterceptor {
    @Autowired
    private UserFactory userFactory;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // get session
        HttpSession session = request.getSession();
        // get user from session
        Object user = session.getAttribute(USER);
        // check user isExist
        if (user == null) {
            //if not, ban it
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            return false;
        }
        // if existed, save user info into ThreadLocal

        UserHolder.saveUser((UserDTO) user);
        // let it go

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
