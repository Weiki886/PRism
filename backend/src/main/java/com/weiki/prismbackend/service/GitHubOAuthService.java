package com.weiki.prismbackend.service;

import com.weiki.prismbackend.model.dto.LoginResponse;
import com.weiki.prismbackend.model.entity.User;
import com.weiki.prismbackend.mapper.UserMapper;
import com.weiki.prismbackend.security.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * GitHub OAuth 登录服务。
 * 流程：前端跳转 GitHub 授权 → 回调拿 code → 后端用 code 换 access_token → 获取用户信息 → 创建/关联本地用户。
 */
@Slf4j
@Service
public class GitHubOAuthService {

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.client-secret}")
    private String clientSecret;

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final WebClient webClient;

    public GitHubOAuthService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.webClient = WebClient.builder().build();
    }

    /**
     * 构建 GitHub OAuth 授权 URL。
     */
    public String buildAuthorizeUrl() {
        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + clientId
                + "&scope=repo read:user";
    }

    /**
     * 用授权 code 换取 access_token，获取 GitHub 用户信息，创建或关联本地用户，返回 JWT。
     */
    @SuppressWarnings("unchecked")
    public LoginResponse handleCallback(String code) {
        // 1. code 换 access_token
        Map<String, Object> tokenResponse = webClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "client_id", clientId,
                        "client_secret", clientSecret,
                        "code", code
                ))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String accessToken = (String) tokenResponse.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("GitHub OAuth 授权失败：" + tokenResponse.get("error_description"));
        }

        // 2. 用 access_token 获取 GitHub 用户信息
        Map<String, Object> ghUser = webClient.get()
                .uri("https://api.github.com/user")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        Long githubId = ((Number) ghUser.get("id")).longValue();
        String githubLogin = (String) ghUser.get("login");
        String avatarUrl = (String) ghUser.get("avatar_url");
        String email = (String) ghUser.get("email");

        // 3. 查找或创建本地用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getGithubId, githubId));

        if (user == null) {
            // 首次 GitHub 登录，自动创建用户
            user = new User();
            user.setUsername(githubLogin);
            user.setEmail(email != null ? email : githubLogin + "@github.com");
            user.setGithubId(githubId);
            user.setGithubLogin(githubLogin);
            user.setGithubToken(accessToken);
            user.setAvatarUrl(avatarUrl);
            user.setRole("USER");
            userMapper.insert(user);
        } else {
            // 已有用户，更新 token 和头像
            user.setGithubToken(accessToken);
            user.setGithubLogin(githubLogin);
            user.setAvatarUrl(avatarUrl);
            userMapper.updateById(user);
        }

        // 4. 签发 JWT
        String jwt = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(jwt, user.getUsername(), user.getRole());
    }
}
