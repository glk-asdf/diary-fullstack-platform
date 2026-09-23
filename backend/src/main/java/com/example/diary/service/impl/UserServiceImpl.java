package com.example.diary.service.impl;

import com.example.diary.common.exception.BizException;
import com.example.diary.common.result.ResultCode;
import com.example.diary.common.util.SecurityUtil;
import com.example.diary.converter.UserConverter;
import com.example.diary.dto.UpdateProfileDTO;
import com.example.diary.entity.User;
import com.example.diary.mapper.UserMapper;
import com.example.diary.service.UserService;
import com.example.diary.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserVO getCurrentUser() {
        return UserConverter.toVO(requireCurrentUser());
    }

    @Override
    @Transactional
    public UserVO updateProfile(UpdateProfileDTO dto) {
        User user = requireCurrentUser();

        if (StringUtils.hasText(dto.nickname())) {
            user.setNickname(dto.nickname());
        }
        if (dto.avatar() != null) {
            user.setAvatar(dto.avatar());
        }
        if (dto.email() != null) {
            user.setEmail(dto.email());
        }

        userMapper.updateById(user);
        return UserConverter.toVO(user);
    }

    private User requireCurrentUser() {
        User user = userMapper.selectById(SecurityUtil.getCurrentUserId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
