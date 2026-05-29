package com.weiki.prismbackend.controller;

import com.weiki.prismbackend.common.Result;
import com.weiki.prismbackend.model.dto.UpdatePasswordRequest;
import com.weiki.prismbackend.model.dto.UpdateProfileRequest;
import com.weiki.prismbackend.security.SecurityUserPrincipal;
import com.weiki.prismbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户", description = "个人信息管理")
@RestController
@RequestMapping("/api/user")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "修改个人信息")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                      @Valid @RequestBody UpdateProfileRequest req) {
        userService.updateProfile(principal.getUserId(), req);
        return Result.success();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> updatePassword(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                       @Valid @RequestBody UpdatePasswordRequest req) {
        userService.updatePassword(principal.getUserId(), req);
        return Result.success();
    }
}
