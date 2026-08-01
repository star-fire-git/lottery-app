package com.example.lottery.strategy;

import com.example.lottery.common.LotteryTypeEnum;
import com.example.lottery.dto.BetInputDTO;
import com.example.lottery.dto.LotteryDrawDTO;
import com.example.lottery.dto.PrizeResultDTO;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 彩票策略接口 —— 策略模式的核心抽象
 * <p>
 * 定义所有彩种必须实现的统一行为契约。每种彩票（双色球、大乐透等）
 * 通过实现本接口来封装各自的玩法规则、数据获取方式和中奖计算逻辑。
 * </p>
 *
 * <h3>调用链路</h3>
 * <pre>
 * Controller → Service → LotteryConfig.getStrategy(type) → LotteryStrategy 具体实现
 * </pre>
 *
 * <h3>新增彩种步骤</h3>
 * <ol>
 *   <li>在 {@link LotteryTypeEnum} 添加枚举值</li>
 *   <li>创建实现类，标注 {@code @Component}</li>
 *   <li>{@link com.example.lottery.config.LotteryConfig} 自动收录</li>
 * </ol>
 */
public interface LotteryStrategy {

    /**
     * 返回该策略对应的彩票类型
     *
     * @return 彩票类型枚举值
     */
    LotteryTypeEnum getType();

    /**
     * 获取最新一期开奖数据
     *
     * @return 开奖数据 DTO
     */
    LotteryDrawDTO fetchLatestDraw();

    /**
     * 根据指定期号获取开奖数据
     *
     * @param periodNo 期号，如 "2025078"
     * @return 开奖数据 DTO
     */
    LotteryDrawDTO fetchDrawByPeriod(String periodNo);

    /**
     * 获取近 N 期可用的期号列表，供前端下拉选择
     * <p>默认返回空列表，子类可按需覆盖。例如双色球调用 API 获取最近 30 期。</p>
     *
     * @param count 需要获取的期数
     * @return 期号列表，按时间倒序（最新在前）
     */
    default List<String> fetchRecentPeriods(int count) {
        return Collections.emptyList();
    }

    /**
     * 核心中奖计算逻辑：比对用户投注号码与开奖号码，返回中奖结果
     *
     * @param drawData 开奖数据
     * @param betInput 用户投注输入（可能包含多注）
     * @return 每注对应的中奖结果列表
     */
    List<PrizeResultDTO> calculatePrize(LotteryDrawDTO drawData, BetInputDTO betInput);

    /**
     * 获取玩法规则说明，用于前端页面展示
     *
     * @return 有序的规则映射表，key=奖项等级，value=中奖条件描述
     */
    Map<String, String> getPrizeRules();

    /**
     * 获取可用玩法列表（快乐八有选一~选十，双色球等单玩法彩种返回空列表）
     * <p>前端据此渲染玩法选择按钮组，切换时更新输入框数量和规则表。</p>
     *
     * @return 玩法列表，每项含 code(编码)、name(名称)、pickCount(选号数)
     */
    default List<Map<String, Object>> getPlayStyles() {
        return Collections.emptyList();
    }

    /**
     * 获取全部玩法的中奖规则（用于支持多玩法的彩种如快乐八）
     * <p>单玩法彩种（如双色球）返回 null，前端据此判断是否显示玩法切换。</p>
     * <p>key=玩法编码(如 x1~x10)，value=该玩法的规则 Map(等级→条件)</p>
     *
     * @return 全部玩法规则映射，单玩法彩种返回 null
     */
    default Map<String, Map<String, String>> getAllPrizeRules() {
        return null;
    }

    /**
     * 获取号码配置信息，供前端动态渲染输入框和选号器
     * <p>不同彩种号码结构差异较大（双色球 6+1，快乐八 10 选 20），
     * 通过此 Map 统一下发参数，避免前端硬编码。</p>
     *
     * @return 配置 Map：inputCount(每注输入框总数), redInputCount(红球/主号码输入数),
     *         blueInputCount(蓝球输入数), maxRed(主号码上限), maxBlue(蓝球上限),
     *         drawnRedCount(开奖主号码数), drawnBlueCount(开奖蓝球数)
     */
    default Map<String, Object> getBallConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("inputCount", 7);
        config.put("redInputCount", 6);
        config.put("blueInputCount", 1);
        config.put("maxRed", 33);
        config.put("maxBlue", 16);
        config.put("drawnRedCount", 6);
        config.put("drawnBlueCount", 1);
        return config;
    }

    /**
     * 获取号码配置描述，如"红球 1-33 选 6 个，蓝球 1-16 选 1 个"
     *
     * @return 号码规则文本
     */
    String getBallDesc();
}
