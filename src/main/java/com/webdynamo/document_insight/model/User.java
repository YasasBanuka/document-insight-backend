package com.webdynamo.document_insight.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column
    private String name;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER; //  USER or ADMIN

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }


//    UserDetails implementation (required by Spring Security)

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Returns the user's roles/permissions
        // ROLE_USER or ROLE_ADMIN
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        // Spring Security needs a "username"
        // We use email as the username
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        // Is the account expired?
        // We don't have account expiration, so always true
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // Is the account locked?
        // We don't have account locking yet, so always true
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // Has the password expired?
        // We don't have password expiration, so always true
        return true;
    }

    @Override
    public boolean isEnabled() {
        // Is the account enabled/active?
        // We don't have account deactivation yet, so always true
        return true;
    }
}
