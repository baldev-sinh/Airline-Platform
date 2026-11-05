package com.baldev.airline.common.payload.request;

import com.baldev.airline.common.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Size(max = 255)
    private String email;

    /** NIST SP 800-63B: length matters more than composition rules; cap at 72 bytes (bcrypt limit). */
    @NotBlank(message = "Password is required")
    @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
    @ToString.Exclude
    private String password;

    @Pattern(regexp = "^\\+[0-9]{6,15}$", message = "Phone number should be in international format, e.g. +919876543210")
    private String phone;

    /** Optional; only ROLE_USER and ROLE_AIRLINE_OWNER may self-register. Defaults to ROLE_USER. */
    private UserRole role;
}
