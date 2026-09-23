package com.example.diary.security;

/**
 * 存入 {@code SecurityContext} 的当前登录用户。
 * <p>只携带鉴权必需的最小信息，避免每次请求都查库。</p>
 */
public record LoginUser(Long userId, String username) {
}
