package com.example.lottery.dto;

import lombok.Data;
import java.util.List;

/**
 * 用户投注输入 DTO —— 前端提交的投注数据
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li><b>API 模式</b>：仅传 lotteryType + periodNo + bets，后端自动拉取开奖数据</li>
 *   <li><b>手动模式</b>：额外传 manualRedBalls / manualBlueBall，后端直接使用用户输入的开奖号码</li>
 * </ul>
 * </p>
 *
 * <h3>API 模式 JSON 示例</h3>
 * <pre>{@code
 * {
 *   "lotteryType": "SSQ",
 *   "periodNo": "2026087",
 *   "bets": [[5, 12, 18, 25, 30, 33, 9]]
 * }
 * }</pre>
 *
 * <h3>手动模式 JSON 示例</h3>
 * <pre>{@code
 * {
 *   "lotteryType": "SSQ",
 *   "manualPeriodNo": "2026087",
 *   "manualDrawDate": "2026-07-30",
 *   "manualRedBalls": [4, 6, 10, 18, 23, 31],
 *   "manualBlueBall": 11,
 *   "bets": [[5, 12, 18, 25, 30, 33, 9]]
 * }
 * }</pre>
 */
@Data
public class BetInputDTO {

    /** 彩票类型编码，如 "SSQ" */
    private String lotteryType;

    /** 目标期号（API 模式），为 null 时默认使用最新一期 */
    private String periodNo;

    /** 投注号码列表，外层每元素为一注，内层前 6 个红球 + 最后 1 个蓝球 */
    private List<List<Integer>> bets;

    /** 快乐八玩法编码（x1 ~ x10），双色球为 null */
    private String kl8PlayStyle;

    // ========== 手动模式字段 ==========

    /** 手动输入的期号 */
    private String manualPeriodNo;

    /** 手动输入的开奖日期 */
    private String manualDrawDate;

    /** 手动输入的红球列表（6 个，1-33） */
    private List<Integer> manualRedBalls;

    /** 手动输入的蓝球（1 个，1-16） */
    private Integer manualBlueBall;

    /**
     * 判断是否为手动模式：只要手动填了红球/主号码即视为手动模式
     * <p>兼容双色球（有蓝球）和快乐八（无蓝球）两种彩种。</p>
     */
    public boolean isManualMode() {
        return manualRedBalls != null && !manualRedBalls.isEmpty();
    }

    /**
     * 将手动输入数据转换为 LotteryDrawDTO，供策略计算使用
     */
    public LotteryDrawDTO toManualDrawDTO() {
        LotteryDrawDTO dto = new LotteryDrawDTO();
        dto.setPeriodNo(manualPeriodNo);
        dto.setDrawDate(manualDrawDate);
        dto.setRedBalls(manualRedBalls);
        dto.setBlueBall(manualBlueBall);
        return dto;
    }
}
