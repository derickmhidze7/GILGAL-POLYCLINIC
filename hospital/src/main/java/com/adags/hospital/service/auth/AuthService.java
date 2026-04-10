package com.adags.hospital.service.auth;

import com.adags.hospital.config.JwtProperties;
import com.adags.hospital.domain.auth.RefreshToken;
import com.adags.hospital.domain.user.AppUser;
import com.adags.hospital.dto.auth.JwtResponse;
import com.adags.hospital.dto.auth.LoginRequest;
import com.adags.hospital.dto.auth.RefreshTokenRequest;
import com.adags.hospital.exception.BusinessRuleException;
import com.adags.hospital.repository.auth.RefreshTokenRepository;
import com.adags.hospital.repository.user.UserRepository;
import com.adags.hospital.security.CustomUserDetailsService;
import com.adags.hospital.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public JwtResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.username());
        AppUser appUser = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String accessToken = jwtService.generateAccessToken(userDetails);
        String rawRefreshToken = jwtService.generateRefreshToken(userDetails);

        // Persist hashed refresh token
        refreshTokenRepository.revokeAllByUser(appUser);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(appUser)
                .tokenHash(hashToken(rawRefreshToken))
                .expiresAt(LocalDateTime.now().plusSeconds(
                        jwtProperties.getRefreshTokenExpirationMs() / 1000))
                .build());

        log.info("User '{}' logged in successfully", request.username());
        return JwtResponse.of(accessToken, rawRefreshToken,
                jwtProperties.getAccessTokenExpirationMs(), appUser.getUsername(), appUser.getRole());
    }

    @Transactional
    public JwtResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.refreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new BusinessRuleException("Invalid or expired refresh token"));

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new BusinessRuleException("Refresh token has expired. Please log in again.");
        }

        AppUser user = storedToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());

        String newAccessToken = jwtService.generateAccessToken(userDetails);
        String newRawRefreshToken = jwtService.generateRefreshToken(userDetails);

        // Rotate refresh token
        storedToken.setRevoked(true);
        storedToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(storedToken);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(newRawRefreshToken))
                .expiresAt(LocalDateTime.now().plusSeconds(
                        jwtProperties.getRefreshTokenExpirationMs() / 1000))
                .build());

        return JwtResponse.of(newAccessToken, newRawRefreshToken,
                jwtProperties.getAccessTokenExpirationMs(), user.getUsername(), user.getRole());
    }

    @Transactional
    public void logout(String username) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        refreshTokenRepository.revokeAllByUser(user);
        log.info("User '{}' logged out — all refresh tokens revoked", username);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
