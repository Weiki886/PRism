package com.weiki.prismbackend.controller;

import com.weiki.prismbackend.common.Result;
import com.weiki.prismbackend.model.dto.LoginRequest;
import com.weiki.prismbackend.model.dto.LoginResponse;
import com.weiki.prismbackend.model.dto.RegisterRequest;
import com.weiki.prismbackend.service.GitHubOAuthService;
import com.weiki.prismbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "认证", description = "注册、登录与 GitHub OAuth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final GitHubOAuthService gitHubOAuthService;

    public AuthController(UserService userService, GitHubOAuthService gitHubOAuthService) {
        this.userService = userService;
        this.gitHubOAuthService = gitHubOAuthService;
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Void> register(@Valid @RequestBody RegisterRequest req) {
        userService.register(req);
        return Result.success();
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(userService.login(req));
    }

    @Operation(summary = "获取 GitHub OAuth 授权 URL")
    @GetMapping("/github")
    public Result<Map<String, String>> githubAuthorizeUrl() {
        String url = gitHubOAuthService.buildAuthorizeUrl();
        return Result.success(Map.of("authorizeUrl", url));
    }

    @Operation(summary = "GitHub OAuth 回调，用 code 换取 token 并登录")
    @PostMapping("/github/callback")
    public Result<LoginResponse> githubCallback(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        LoginResponse response = gitHubOAuthService.handleCallback(code);
        return Result.success(response);
    }
}
