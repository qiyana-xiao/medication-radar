package com.medicationradar.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medicationradar.common.ApiException;
import com.medicationradar.entity.LoginLog;
import com.medicationradar.entity.User;
import com.medicationradar.mapper.LoginLogMapper;
import com.medicationradar.mapper.UserMapper;
import com.medicationradar.security.AuthUser;
import com.medicationradar.security.JwtService;
import com.medicationradar.security.TokenRevocationService;
import com.medicationradar.service.PasswordService;
import com.medicationradar.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_\\u4e00-\\u9fa5]{2,20}$");
    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final TokenRevocationService tokenRevocationService;

    public AuthController(UserMapper userMapper, LoginLogMapper loginLogMapper,
                          PasswordService passwordService, JwtService jwtService,
                          RateLimitService rateLimitService, TokenRevocationService tokenRevocationService) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.rateLimitService = rateLimitService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> register(@Valid @RequestBody RegisterInput input, HttpServletRequest request) {
        enforceRateLimit(request);
        if (!USERNAME.matcher(input.username()).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户名需为 2-20 位字母/数字/下划线/中文");
        }
        if (input.password().length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码至少 6 位");
        }
        String role = input.role() == null ? "patient" : input.role().trim();
        if (!role.equals("patient") && !role.equals("doctor")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "注册角色只能选 patient 或 doctor");
        }
        User existing = findByUsername(input.username());
        if (existing != null) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已被注册");
        }
        User user = new User();
        user.setUsername(input.username());
        user.setPasswordHash(passwordService.encode(input.password()));
        user.setRole(role);
        userMapper.insert(user);
        AuthUser authUser = toAuthUser(user);
        writeLoginLog(user, user.getUsername(), true, null, request);
        return Map.of("token", jwtService.issue(authUser), "user", authUser);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody Credentials input, HttpServletRequest request) {
        enforceRateLimit(request);
        User user = findByUsername(input.username());
        if (user == null || !passwordService.matches(input.password(), user.getPasswordHash())) {
            writeLoginLog(user, input.username(), false, "INVALID_CREDENTIALS", request);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        AuthUser authUser = toAuthUser(user);
        writeLoginLog(user, user.getUsername(), true, null, request);
        return Map.of("token", jwtService.issue(authUser), "user", authUser);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AuthUser principal) {
        User user = userMapper.selectById(principal.id());
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }
        return Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole(),
                "createdAt", user.getCreatedAt());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            JwtService.TokenClaims claims = jwtService.parseClaims(header.substring(7));
            tokenRevocationService.revoke(claims.tokenId(), claims.expiresAt());
        }
        return Map.of("ok", true);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private AuthUser toAuthUser(User user) {
        return new AuthUser(user.getId(), user.getUsername(), user.getRole());
    }

    private void enforceRateLimit(HttpServletRequest request) {
        if (!rateLimitService.allow("auth", clientIp(request), 15, Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
        }
    }

    private void writeLoginLog(User user, String attemptedUsername, boolean success, String reason,
                               HttpServletRequest request) {
        LoginLog log = new LoginLog();
        log.setUserId(user == null ? null : user.getId());
        log.setUsername(user == null ? attemptedUsername : user.getUsername());
        log.setSuccess(success);
        log.setFailureReason(reason);
        log.setIpAddress(clientIp(request));
        String agent = request.getHeader("User-Agent");
        log.setUserAgent(agent == null ? null : agent.substring(0, Math.min(agent.length(), 500)));
        loginLogMapper.insert(log);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
    }

    public record Credentials(@NotBlank(message = "请输入用户名和密码") String username,
                              @NotBlank(message = "请输入用户名和密码") String password) {
    }

    public record RegisterInput(@NotBlank(message = "请输入用户名和密码") String username,
                                @NotBlank(message = "请输入用户名和密码") String password,
                                String role) {
    }
}
