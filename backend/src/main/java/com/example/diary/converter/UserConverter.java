package com.example.diary.converter;

import com.example.diary.entity.User;
import com.example.diary.vo.UserVO;

/**
 * 用户实体 → 出参转换。
 * <p>出参刻意不含 password，集中在此转换可避免各处遗漏。</p>
 */
public final class UserConverter {

    private UserConverter() {
    }

    public static UserVO toVO(User user) {
        if (user == null) {
            return null;
        }
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(), user.getEmail());
    }
}
