package com.adags.hospital.config;

import com.adags.hospital.security.CustomUserDetailsService;
import com.adags.hospital.security.JwtAuthenticationFilter;
import com.adags.hospital.security.LoginFailureHandler;
import com.adags.hospital.security.RoleBasedSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final RoleBasedSuccessHandler roleBasedSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;

    private static final String[] SWAGGER_ENDPOINTS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    // ----------------------------------------------------------------
    //  Web portal — single form-login, session-based (@Order(1))
    //  One /login page; RoleBasedSuccessHandler routes each user role.
    // ----------------------------------------------------------------
    @Bean
    @Order(1)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/", "/login", "/logout", "/error",
                             "/admin/**", "/receptionist/**", "/nurse/**", "/ward-nurse/**",
                             "/doctor/**", "/labtech/**", "/pharmacist/**",
                             "/css/**", "/js/**", "/images/**")
            .csrf(AbstractHttpConfigurer::disable)
            .securityContext(sc -> sc.requireExplicitSave(false))
            .sessionManagement(sm -> sm
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation().changeSessionId()
            )
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/login", "/error", "/css/**", "/js/**", "/images/**").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers("/receptionist/**").hasRole("RECEPTIONIST")
                    .requestMatchers("/nurse/**").hasAnyRole("TRIAGE_NURSE", "NURSE")
                    .requestMatchers("/ward-nurse/**").hasAnyRole("WARD_NURSE", "NURSE")
                    .requestMatchers("/doctor/**").hasAnyRole("DOCTOR", "SPECIALIST_DOCTOR")
                    .requestMatchers("/labtech/**").hasRole("LAB_TECHNICIAN")
                    .requestMatchers("/pharmacist/**").hasRole("PHARMACIST")
                    .anyRequest().authenticated()
            )
            .formLogin(form -> form
                    .loginPage("/login")
                    .loginProcessingUrl("/login")
                    .successHandler(roleBasedSuccessHandler)
                    .failureHandler(loginFailureHandler)
                    .permitAll()
            )
            .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout=true")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
            )
            .authenticationProvider(authenticationProvider());
        return http.build();
    }

    // ----------------------------------------------------------------
    //  REST API — JWT stateless (@Order(2))
    // ----------------------------------------------------------------
    @Bean
    @Order(2)
    public SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/**", "/actuator/health").permitAll()
                    .requestMatchers(SWAGGER_ENDPOINTS).hasRole("ADMIN")
                    .anyRequest().authenticated()
            )
            .headers(headers -> headers
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(ct -> {})
                    .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
