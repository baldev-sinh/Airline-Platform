package com.baldev.airline.user.service.impl;

import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.payload.dto.UserDTO;
import com.baldev.airline.user.mapper.UserMapper;
import com.baldev.airline.user.repository.UserRepository;
import com.baldev.airline.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserDTO getUserById(Long id) {
        return userRepository.findById(id)
                .map(UserMapper::toDTO)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    @Override
    public Page<UserDTO> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserMapper::toDTO);
    }
}
