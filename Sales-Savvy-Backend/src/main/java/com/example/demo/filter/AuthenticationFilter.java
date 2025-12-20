package com.example.demo.filter;

import com.example.demo.entities.Role;
import com.example.demo.entities.User;
import com.example.demo.repositories.UserRepository;
import com.example.demo.services.AuthService;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@WebFilter(urlPatterns = {"/api/*","/admin/*"})

@Component
public class AuthenticationFilter implements Filter {

    private static final Logger logger =
            LoggerFactory.getLogger(AuthenticationFilter.class);

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private static final String[] UNAUTHENTICATED_PATHS = {
        "/api/users/register",
        "/api/auth/login",
        "/api/auth/logout"
    };

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthenticationFilter(AuthService authService,
                                UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
        System.out.println("Filter Started.");
    }

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest =
                (HttpServletRequest) request;
        HttpServletResponse httpResponse =
                (HttpServletResponse) response;

        try {
            executeFilterLogic(httpRequest, httpResponse, chain);
        } catch (Exception e) {
            logger.error("Unexpected error in AuthenticationFilter", e);
            sendErrorResponse(
                    httpResponse,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error"
            );
        }
    }

    private void executeFilterLogic(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws IOException, ServletException {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        logger.info("Request URI: {}", requestURI);

        // ✅ 1. HANDLE CORS PREFLIGHT FIRST
        if ("OPTIONS".equalsIgnoreCase(method)) {
            setCORSHeaders(response);
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // ✅ 2. ALLOW PUBLIC ENDPOINTS
        if (Arrays.asList(UNAUTHENTICATED_PATHS).contains(requestURI)) {
            chain.doFilter(request, response);
            return;
        }

        // ✅ 3. READ TOKEN FROM COOKIE
        String token = getAuthTokenFromCookies(request);

        if (token == null || !authService.validateToken(token)) {
            sendErrorResponse(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized: Invalid or missing token"
            );
            return;
        }

        // ✅ 4. EXTRACT USER
        String username = authService.extractUsername(token);
        Optional<User> userOptional =
                userRepository.findByUsername(username);

        if (userOptional.isEmpty()) {
            sendErrorResponse(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized: User not found"
            );
            return;
        }

        User authenticatedUser = userOptional.get();
        Role role = authenticatedUser.getRole();

        // ✅ 5. ROLE CHECK
        if (requestURI.startsWith("/admin/")
                && role != Role.ADMIN) {
            sendErrorResponse(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Forbidden: Admin access required"
            );
            return;
        }

        if (requestURI.startsWith("/api/")
                && role != Role.CUSTOMER) {
            sendErrorResponse(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Forbidden: Customer access required"
            );
            return;
        }

        // ✅ 6. ATTACH USER & CONTINUE
        request.setAttribute("authenticatedUser", authenticatedUser);
        chain.doFilter(request, response);
    }

    // ================= CORS =================

    private void setCORSHeaders(HttpServletResponse response) {
        response.setHeader(
                "Access-Control-Allow-Origin",
                ALLOWED_ORIGIN
        );
        response.setHeader(
                "Access-Control-Allow-Credentials",
                "true"
        );
        response.setHeader(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization"
        );
        response.setHeader(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS"
        );
    }

    private void sendErrorResponse(HttpServletResponse response,
                                   int statusCode,
                                   String message)
            throws IOException {

        setCORSHeaders(response); // 🔥 IMPORTANT
        response.setStatus(statusCode);
        response.getWriter().write(message);
    }

    private String getAuthTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> "authToken".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}