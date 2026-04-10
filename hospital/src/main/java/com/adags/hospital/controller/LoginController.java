package com.adags.hospital.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String reason,
                        HttpSession session,
                        Model model) {
        if (error != null) {
            // Try to get the specific reason from the session (set by our failure handler)
            String detail = (String) session.getAttribute("SPRING_SECURITY_LAST_EXCEPTION_MESSAGE");
            if (detail != null) {
                model.addAttribute("errorMsg", detail);
                session.removeAttribute("SPRING_SECURITY_LAST_EXCEPTION_MESSAGE");
            } else {
                model.addAttribute("errorMsg", "Invalid username or password.");
            }
        }
        if (logout != null) {
            model.addAttribute("logoutMsg", "You have been logged out successfully.");
        }
        return "login";
    }
}
