package com.baldev.airline.user.service;

import com.baldev.airline.common.payload.dto.UserDTO;

import java.util.List;

public interface UserService {
    UserDTO getUserByEmail(String email) throws Exception;
    UserDTO getUserById(Long id) throws Exception;
    List<UserDTO> getAllUsers();
}
