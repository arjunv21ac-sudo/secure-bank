package com.securebank.security;

import com.securebank.entity.User;
import com.securebank.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

// Tells Spring Security how to load a user (by email) from our database
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // "ROLE_" prefix is what hasRole("ADMIN") checks for.
        // accountNonLocked = false makes Spring Security refuse the login with LockedException,
        // even when the password is correct.
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                true,              // enabled
                true,              // account not expired
                true,              // credentials not expired
                !user.isLocked(),  // account not locked
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
