package com.stock.dashboard.backend.service;

import com.stock.dashboard.backend.model.User;
import com.stock.dashboard.backend.repository.UserRepository;
import com.stock.dashboard.backend.security.model.CustomUserDetails;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info("Loading user by email: {}", email);


        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("해당 계정이 없습니다."));

        log.info("DB password hash: {}", user.getPassword());
        log.info("User found: id={}, email={}", user.getId(), user.getEmail());

        return new CustomUserDetails(user);
    }

    public UserDetails loadUserById(Long id) {


        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("해당 계정이 없습니다."));
        log.info("Fetched user : {} by id {}", user.getEmail(), id);
        return new CustomUserDetails(user);
    }
}
