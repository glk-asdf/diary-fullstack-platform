package com.example.diary.service;

import com.example.diary.dto.LoginDTO;
import com.example.diary.dto.RegisterDTO;
import com.example.diary.vo.TokenVO;
import com.example.diary.vo.UserVO;

public interface AuthService {

    /** 注册，成功后直接返回用户信息（不自动登录） */
    UserVO register(RegisterDTO dto);

    /** 登录，签发 Access Token 与 Refresh Token */
    TokenVO login(LoginDTO dto);

    /** 用 Refresh Token 换取新的令牌对（旧 Refresh Token 会被轮换失效） */
    TokenVO refresh(String refreshToken);

    /** 登出，使当前 Refresh Token 立即失效 */
    void logout(String refreshToken);
}
