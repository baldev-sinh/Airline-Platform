package com.baldev.airline.user.service.impl;

import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.user.model.User;
import com.baldev.airline.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializationComponent implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        initializeAdminUser();
    }

    private void initializeAdminUser(){
        String email="baldev@gmail.com";
        String password="test123";

        if(userRepository.findByEmail(email)==null){
            User adminUser= User
                    .builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .fullName("baldev")
                    .role(UserRole.ROLE_SYSTEM_ADMIN)
                    .build();

            User admin=userRepository.save(adminUser);
        }
    }
}

