package com.example.lottery.dto;

import lombok.Data;
import java.util.List;

/**
 * 中奖结果 DTO —— 单注投注的匹配详情和奖金信息
 * <p>
 * 每次计算返回一个列表（支持多注），前端据此渲染中奖结果卡片。
 * </p>
 */
@Data
public class PrizeResultDTO {

    /** 对应期号 */
    private String periodNo;

    /** 投注序号，从 1 开始 */
    private int betIndex;

    /** 红球命中个数 */
    private int redMatchCount;

    /** 蓝球是否命中 */
    private boolean blueMatch;

    /** 命中的具体红球号码列表 */
    private List<Integer> matchedRedBalls;

    /** 中奖等级，如 "一等奖"、"未中奖" */
    private String prizeLevel;

    /** 奖金描述文本，如 "3000元" */
    private String prizeAmount;

    /** 是否中奖 */
    private boolean won;

    /** 人类可读的结果描述 */
    private String description;
}
