package com.example.diary.service;

import com.example.diary.vo.DayCountVO;
import com.example.diary.vo.StatsOverviewVO;

import java.util.List;

public interface StatsService {

    /** 指定年份每天的写作篇数，用于热力图 */
    List<DayCountVO> calendar(int year);

    /** 总篇数、本月篇数、连续打卡天数与心情分布 */
    StatsOverviewVO overview();
}
