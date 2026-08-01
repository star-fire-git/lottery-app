package com.example.lottery.service;

import com.example.lottery.common.LotteryTypeEnum;
import com.example.lottery.config.LotteryConfig;
import com.example.lottery.dto.LotteryDrawDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 开奖数据服务 —— 封装策略调用，提供统一的数据获取入口
 * <p>
 * 根据彩票类型从 {@link LotteryConfig} 获取对应策略，再委托策略完成实际数据获取。
 * Service 层不关心具体是双色球还是大乐透，只依赖接口编程。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class LotteryDataService {

    /** 策略注册中心 */
    private final LotteryConfig lotteryConfig;

    /**
     * 获取指定彩种最新一期开奖数据
     *
     * @param type 彩票类型
     * @return 最新开奖数据
     * @throws IllegalArgumentException 彩票类型不支持时抛出
     */
    public LotteryDrawDTO getLatestDraw(LotteryTypeEnum type) {
        return lotteryConfig.getStrategy(type)
                .map(strategy -> strategy.fetchLatestDraw())
                .orElseThrow(() -> new IllegalArgumentException("不支持的彩票类型: " + type));
    }

    /**
     * 根据期号获取指定彩种的开奖数据
     *
     * @param type     彩票类型
     * @param periodNo 期号
     * @return 对应期号的开奖数据
     * @throws IllegalArgumentException 彩票类型不支持时抛出
     */
    public LotteryDrawDTO getDrawByPeriod(LotteryTypeEnum type, String periodNo) {
        return lotteryConfig.getStrategy(type)
                .map(strategy -> strategy.fetchDrawByPeriod(periodNo))
                .orElseThrow(() -> new IllegalArgumentException("不支持的彩票类型: " + type));
    }

    /**
     * 获取近 N 期可用的期号列表，供前端下拉选择
     *
     * @param type  彩票类型
     * @param count 需要获取的期数
     * @return 期号列表，按时间倒序
     */
    public List<String> getRecentPeriods(LotteryTypeEnum type, int count) {
        return lotteryConfig.getStrategy(type)
                .map(strategy -> strategy.fetchRecentPeriods(count))
                .orElse(Collections.emptyList());
    }
}
