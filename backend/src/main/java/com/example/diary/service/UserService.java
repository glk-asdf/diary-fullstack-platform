package com.example.diary.service;

import com.example.diary.dto.UpdateProfileDTO;
import com.example.diary.vo.UserVO;

public interface UserService {

    /** 当前登录用户信息 */
    UserVO getCurrentUser();

    /** 修改昵称 / 头像 / 邮箱，null 字段表示不修改 */
    UserVO updateProfile(UpdateProfileDTO dto);
}
