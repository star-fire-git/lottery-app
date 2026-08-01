package com.example.lottery.service;

import com.example.lottery.common.LotteryTypeEnum;
import com.example.lottery.config.LotteryConfig;
import com.example.lottery.dto.BetInputDTO;
import com.example.lottery.dto.LotteryDrawDTO;
import com.example.lottery.dto.PrizeResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 中奖计算服务 —— 编排开奖数据获取与中奖匹配流程
 * <p>
 * 先根据期号获取开奖数据（为空则取最新），再委托对应策略完成号码匹配计算。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PrizeCalcService {

    /** 策略注册中心 */
    private final LotteryConfig lotteryConfig;

    /**
     * 执行中奖计算
     * <p>若 periodNo 为 null 或空，默认使用该彩种最新一期开奖数据。</p>
     *
     * @param type      彩票类型
     * @param periodNo  目标期号（可为 null）
     * @param betInput  用户投注输入
     * @return 每注对应的中奖结果列表
     * @throws IllegalArgumentException 彩票类型不支持时抛出
     */
    public List<PrizeResultDTO> calculate(LotteryTypeEnum type, String periodNo, BetInputDTO betInput) {
        return lotteryConfig.getStrategy(type)
                .map(strategy -> {
                    // 获取开奖数据：指定期号优先，否则取最新
                    LotteryDrawDTO drawData;
                    if (periodNo != null && !periodNo.isEmpty()) {
                        drawData = strategy.fetchDrawByPeriod(periodNo);
                    } else {
                        drawData = strategy.fetchLatestDraw();
                    }
                    return strategy.calculatePrize(drawData, betInput);
                })
                .orElseThrow(() -> new IllegalArgumentException("不支持的彩票类型: " + type));
    }

    /**
     * 使用手动输入的开奖数据直接计算中奖（跳过 API 调用）
     *
     * @param type           彩票类型
     * @param manualDrawData 用户手动输入的开奖数据
     * @param betInput       用户投注输入
     * @return 每注对应的中奖结果列表
     */
    public List<PrizeResultDTO> calculateWithManualData(LotteryTypeEnum type, LotteryDrawDTO manualDrawData, BetInputDTO betInput) {
        return lotteryConfig.getStrategy(type)
                .map(strategy -> strategy.calculatePrize(manualDrawData, betInput))
                .orElseThrow(() -> new IllegalArgumentException("不支持的彩票类型: " + type));
    }

}
