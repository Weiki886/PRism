package com.weiki.prismbackend.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SecurityUserPrincipal {
    private final Long userId;
    private final String username;
}
