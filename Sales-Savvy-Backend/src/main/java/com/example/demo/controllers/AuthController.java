package com.example.demo.controllers;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.demo.dtos.LoginRequest;
import com.example.demo.entities.User;
import com.example.demo.services.AuthService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/api/auth")
@CrossOrigin (origins = "http://localhost:5173", allowCredentials = "true")
public class AuthController {
	
	private final AuthService authService;
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        try {
            User user = authService.authenticate(loginRequest.getUsername(), loginRequest.getPassword());
            String token = authService.generateToken(user);

            Cookie cookie = new Cookie("authToken", token);
            cookie.setHttpOnly(true);
            cookie.setSecure(false); // Set to true if using HTTPS
            cookie.setPath("/");
            cookie.setMaxAge(3600); // 1 hour
            cookie.setDomain("localhost");
            response.addCookie(cookie);
           // Optional but useful
            
            response.addHeader("Set-Cookie",
                    String.format("authToken=%s; HttpOnly; Path=/; Max-Age=3600; SameSite=None", token));

            
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Login successful");
            responseBody.put("role", user.getRole().name());
            responseBody.put("username", user.getUsername());

            return ResponseEntity.ok(responseBody);
            
        } 
        catch (RuntimeException e) 
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            // Extract token from cookie
            String token = null;
            Cookie[] cookies = request.getCookies();
            
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("authToken".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
            
            if (token == null || token.isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("message", "No authentication token found");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            // Validate token and extract username
            if (!authService.validateToken(token)) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("message", "Invalid or expired token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            String username = authService.extractUsername(token);
            User user = authService.getUserByUsername(username); // You'll need to add this method
            
            // Perform logout
            authService.logout(user);
            
            // Clear the cookie
            Cookie cookie = new Cookie("authToken", null);
            cookie.setHttpOnly(true);
            cookie.setMaxAge(0);
            cookie.setPath("/");
            cookie.setDomain("localhost");
            response.addCookie(cookie);
            
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("message", "Logout successful");
            return ResponseEntity.ok(responseBody);
            
        } catch (Exception e) {
            System.err.println("Logout error: " + e.getMessage());
            e.printStackTrace();
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Logout failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

}
