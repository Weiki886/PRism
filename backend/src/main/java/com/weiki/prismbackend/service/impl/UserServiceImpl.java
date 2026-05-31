package com.weiki.prismbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weiki.prismbackend.common.ResultCode;
import com.weiki.prismbackend.exception.BusinessException;
import com.weiki.prismbackend.mapper.UserMapper;
import com.weiki.prismbackend.model.dto.*;
import com.weiki.prismbackend.model.entity.User;
import com.weiki.prismbackend.security.JwtUtil;
import com.weiki.prismbackend.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void register(RegisterRequest req) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername())) > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail())) > 0) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("USER");
        userMapper.insert(user);
    }

    @Override
    public LoginResponse login(LoginRequest req) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.WRONG_PASSWORD);
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getRole(), user.getAvatarUrl(), user.getGithubLogin());
    }

    @Override
    public void updateProfile(Long userId, UpdateProfileRequest req) {
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()).ne(User::getId, userId));
        if (existing != null) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }
        User user = new User();
        user.setId(userId);
        user.setEmail(req.getEmail());
        userMapper.updateById(user);
    }

    @Override
    public void updatePassword(Long userId, UpdatePasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.WRONG_PASSWORD);
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(update);
    }

    @Override
    public List<UserDTO> listAllUsers() {
        return userMapper.selectList(null).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public UserDTO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toDTO(user);
    }

    @Override
    public void updateUserRole(Long id, String role) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        User update = new User();
        update.setId(id);
        update.setRole(role);
        userMapper.updateById(update);
    }

    @Override
    public void deleteUser(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        userMapper.deleteById(id);
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        return dto;
    }
}
