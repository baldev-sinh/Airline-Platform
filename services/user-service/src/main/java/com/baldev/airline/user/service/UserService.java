package com.baldev.airline.user.service;

import com.baldev.airline.common.payload.dto.UserDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserDTO getUserById(Long id);

    Page<UserDTO> getAllUsers(Pageable pageable);
}
