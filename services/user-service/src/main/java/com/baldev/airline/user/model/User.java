package com.baldev.airline.user.model;

import com.baldev.airline.common.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    /** Stored lower-cased so uniqueness and login are case-insensitive. */
    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @ToString.Exclude
    @Column(nullable = false, length = 100)
    private String password;

    /** Stored by name: ordinal storage silently corrupts roles if the enum is ever reordered. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private LocalDateTime lastLogin;
}
