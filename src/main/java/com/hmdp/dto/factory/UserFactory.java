package com.hmdp.dto.factory;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

@Component
public class UserFactory {

    public UserDTO toDto(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .icon(user.getIcon())
                .nickName(user.getNickName())
                .build();
    }

}
