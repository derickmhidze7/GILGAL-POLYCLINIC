package com.adags.hospital.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * After a successful login, redirects the user to the dashboard
 * that matches their role — no portal selector needed.
 */
@Component
public class RoleBasedSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        String redirectUrl = "/login?error=role";   // fallback

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            switch (authority.getAuthority()) {
                case "ROLE_ADMIN"         -> redirectUrl = "/admin/dashboard";
                case "ROLE_RECEPTIONIST"  -> redirectUrl = "/receptionist/dashboard";
                case "ROLE_TRIAGE_NURSE"  -> redirectUrl = "/nurse/dashboard";
                case "ROLE_NURSE"          -> redirectUrl = "/nurse/dashboard";
                case "ROLE_WARD_NURSE"     -> redirectUrl = "/ward-nurse/dashboard";
                case "ROLE_DOCTOR",
                     "ROLE_SPECIALIST_DOCTOR" -> redirectUrl = "/doctor/dashboard";
                case "ROLE_LAB_TECHNICIAN"  -> redirectUrl = "/labtech/dashboard";
                case "ROLE_PHARMACIST"       -> redirectUrl = "/pharmacist/dashboard";
                default -> {}
            }
            if (!redirectUrl.equals("/login?error=role")) break;
        }

        response.sendRedirect(request.getContextPath() + redirectUrl);
    }
}
