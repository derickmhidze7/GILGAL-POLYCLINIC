package com.adags.hospital.security;

import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Combine role (ROLE_XXX) + fine-grained permissions as granted authorities
        var authorities = Stream.concat(
                Stream.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getRole().getPermissions().stream()
                        .map(p -> new SimpleGrantedAuthority(p.name()))
        ).collect(Collectors.toList());

        return User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .accountLocked(!user.isAccountNonLocked())
                .disabled(!user.isEnabled())
                .build();
    }
}
