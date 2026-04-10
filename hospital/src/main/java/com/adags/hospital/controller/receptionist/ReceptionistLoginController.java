package com.adags.hospital.controller.receptionist;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/receptionist")
public class ReceptionistLoginController {

    @GetMapping("/login")
    public String login(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model) {
        if (error != null) {
            model.addAttribute("errorMsg", "Invalid username or password.");
        }
        if (logout != null) {
            model.addAttribute("successMsg", "You have been logged out.");
        }
        return "receptionist/login";
    }
}
