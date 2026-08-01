package com.example.lottery.dto;

import lombok.Data;
import java.util.List;

/**
 * 开奖数据 DTO —— 所有彩种通用的开奖信息载体
 * <p>
 * 包含期号、开奖日期、中奖号码（红球/蓝球）、奖池金额及各等级中奖详情。
 * 个别彩种如有特殊字段可通过继承扩展。
 * </p>
 */
@Data
public class LotteryDrawDTO {

    /** 期号，如 "2025078" */
    private String periodNo;

    /** 开奖日期，格式 yyyy-MM-dd */
    private String drawDate;

    /** 红球号码列表（双色球为 6 个） */
    private List<Integer> redBalls;

    /** 蓝球号码（双色球为 1 个） */
    private Integer blueBall;

    /** 奖池金额（单位：元，字符串形式保留精度） */
    private String poolAmount;

    /** 各等级中奖详情 */
    private List<PrizeDetail> prizeDetails;

    /**
     * 中奖等级详情内部类
     */
    @Data
    public static class PrizeDetail {
        /** 奖项等级名称，如 "一等奖" */
        private String level;
        /** 该等级中奖注数 */
        private int count;
        /** 该等级单注奖金 */
        private String amount;
    }
}
