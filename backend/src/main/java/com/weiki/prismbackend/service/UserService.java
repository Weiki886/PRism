package com.weiki.prismbackend.service;

import com.weiki.prismbackend.model.dto.*;

import java.util.List;

public interface UserService {

    void register(RegisterRequest req);

    LoginResponse login(LoginRequest req);

    void updateProfile(Long userId, UpdateProfileRequest req);

    void updatePassword(Long userId, UpdatePasswordRequest req);

    List<UserDTO> listAllUsers();

    UserDTO getUserById(Long id);

    void updateUserRole(Long id, String role);

    void deleteUser(Long id);
}
