package com.adags.hospital.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Stores a human-readable error message in the session so the login page
 * can display the specific reason for failure (wrong password vs disabled account).
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public LoginFailureHandler() {
        super("/login?error=true");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String message;
        if (exception instanceof DisabledException) {
            message = "This account has been disabled. Please contact the administrator.";
        } else if (exception instanceof LockedException) {
            message = "This account is locked. Please contact the administrator.";
        } else if (exception instanceof BadCredentialsException
                || exception instanceof UsernameNotFoundException) {
            message = "Invalid username or password.";
        } else {
            message = "Login failed: " + exception.getMessage();
        }

        request.getSession().setAttribute("SPRING_SECURITY_LAST_EXCEPTION_MESSAGE", message);
        super.onAuthenticationFailure(request, response, exception);
    }
}
