package com.baldev.airline.user.mapper;

import com.baldev.airline.user.payload.dto.UserDTO;
import com.baldev.airline.user.model.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserDTO toDTO(User user) {
        if (user == null) {
            return null;
        }
        return UserDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .lastLogin(user.getLastLogin())
                .phone(user.getPhone())
                .build();
    }
}
