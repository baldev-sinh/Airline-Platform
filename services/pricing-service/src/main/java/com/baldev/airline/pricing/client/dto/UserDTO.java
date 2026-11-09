package com.baldev.airline.pricing.client.dto;

import com.baldev.airline.common.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Public view of a user. Never carries credentials. */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private UserRole role;
    private LocalDateTime lastLogin;
}
