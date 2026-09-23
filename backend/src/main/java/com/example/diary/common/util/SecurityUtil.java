package com.example.diary.common.util;

import com.example.diary.common.exception.BizException;
import com.example.diary.common.result.ResultCode;
import com.example.diary.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户读取工具。
 * <p>所有需要数据隔离的业务都应通过这里拿 userId，禁止从请求参数取。</p>
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static LoginUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().userId();
    }

    public static String getCurrentUsername() {
        return getCurrentUser().username();
    }
}
