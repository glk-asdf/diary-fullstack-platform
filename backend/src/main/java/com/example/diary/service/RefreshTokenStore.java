package com.example.diary.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Refresh Token 的 Redis 存储。
 * <p>Key 结构：{@code diary:refresh:{userId}:{jti}}，value 为用户名。
 * 按 userId 前缀组织，便于「踢下线」时一次性清除该用户的全部令牌。</p>
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "diary:refresh:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(Long userId, String jti, String username, long ttlSeconds) {
        redisTemplate.opsForValue().set(buildKey(userId, jti), username, Duration.ofSeconds(ttlSeconds));
    }

    public boolean exists(Long userId, String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(userId, jti)));
    }

    /** 登出：只失效当前这一枚令牌 */
    public void remove(Long userId, String jti) {
        redisTemplate.delete(buildKey(userId, jti));
    }

    /**
     * 踢下线：清除该用户全部 Refresh Token。
     * <p>使用 KEYS 匹配，属低频管理操作，可接受；若后续并发量增大可换成 SCAN。</p>
     */
    public long removeAll(Long userId) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        return redisTemplate.delete(keys);
    }

    private String buildKey(Long userId, String jti) {
        return KEY_PREFIX + userId + ":" + jti;
    }
}
