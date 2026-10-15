package com.selfhealing.backend.controller;

import com.selfhealing.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@CrossOrigin(origins = "${cors.allowed-origins}")
@RestController
public class AuthController {

    private final JwtUtil jwtUtil;
    private final com.selfhealing.backend.repository.UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtUtil jwtUtil, 
                          com.selfhealing.backend.repository.UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        var userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent() && passwordEncoder.matches(password, userOpt.get().getPasswordHash())) {
            String token = jwtUtil.generateToken(username);
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "type", "Bearer",
                    "expiresIn", "3600s",
                    "role", userOpt.get().getRole()
            ));
        }

        return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid credentials"
        ));
    }
}
