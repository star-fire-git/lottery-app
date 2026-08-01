package com.example.lottery.strategy;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.lottery.common.LotteryTypeEnum;
import com.example.lottery.dto.BetInputDTO;
import com.example.lottery.dto.LotteryDrawDTO;
import com.example.lottery.dto.PrizeResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 快乐八（KL8）策略实现 —— 支持选一至选十全部 10 种玩法
 * <p>
 * 快乐八是中国福利彩票的一种快速开奖游戏，每天开奖。
 * 每期从 1-80 中开出 20 个中奖号码，用户根据所选玩法（选一~选十）
 * 选取对应数量的号码，按命中个数对照该玩法的奖级表判定是否中奖。
 * </p>
 *
 * <h3>各玩法中奖规则速查</h3>
 * <pre>
 * 选一：中1 → 4.6元
 * 选二：中2 → 19元
 * 选三：中3 → 53元 | 中2 → 3元
 * 选四：中4 → 100元 | 中3 → 5元 | 中2 → 3元
 * 选五：中5 → 1000元 | 中4 → 20元 | 中3 → 3元
 * 选六：中6 → 3000元 | 中5 → 30元 | 中4 → 10元 | 中3 → 3元
 * 选七：中7 → 10000元 | 中6 → 288元 | 中5 → 28元 | 中4 → 4元 | 中0 → 2元
 * 选八：中8 → 50000元 | 中7 → 800元 | 中6 → 88元 | 中5 → 10元 | 中4 → 3元 | 中0 → 2元
 * 选九：中9 → 浮动(30万) | 中8 → 2000元 | 中7 → 200元 | 中6 → 20元 | 中5 → 5元 | 中4 → 3元 | 中0 → 2元
 * 选十：中10 → 浮动(最高500万) | 中9 → 8000元 | 中8 → 800元 | 中7 → 80元 | 中6 → 5元 | 中5 → 3元 | 中0 → 2元
 * </pre>
 * <p>注意：选七~选十有「一个都不中」保底奖 2 元。</p>
 */
@Slf4j
@Component
public class Kl8Strategy implements LotteryStrategy {

    /** 中国福利彩票开奖查询 API —— 快乐八 */
    private static final String API_BASE =
            "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=kl8";

    /** 按期号查询时拉取的批量期数 */
    private static final int BATCH_SIZE = 100;

    // ==================== 10 种玩法定义 ====================

    /**
     * 单个奖级定义：命中 N 个号码对应的奖项与奖金
     *
     * @param matchCount 需要命中几个号码（0 表示"全不中"保底奖）
     * @param level      奖项等级名称
     * @param amount     奖金描述（固定金额或"浮动"）
     */
    private record PrizeTier(int matchCount, String level, String amount) {}

    /**
     * 快乐八玩法定义：编码、名称、选号数、奖级表
     */
    public enum PlayStyle {
        X1 ("x1",  "选一",  1,
                new PrizeTier(1, "一等奖", "4.6元")),
        X2 ("x2",  "选二",  2,
                new PrizeTier(2, "一等奖", "19元")),
        X3 ("x3",  "选三",  3,
                new PrizeTier(3, "一等奖", "53元"),
                new PrizeTier(2, "二等奖", "3元")),
        X4 ("x4",  "选四",  4,
                new PrizeTier(4, "一等奖", "100元"),
                new PrizeTier(3, "二等奖", "5元"),
                new PrizeTier(2, "三等奖", "3元")),
        X5 ("x5",  "选五",  5,
                new PrizeTier(5, "一等奖", "1000元"),
                new PrizeTier(4, "二等奖", "20元"),
                new PrizeTier(3, "三等奖", "3元")),
        X6 ("x6",  "选六",  6,
                new PrizeTier(6, "一等奖", "3000元"),
                new PrizeTier(5, "二等奖", "30元"),
                new PrizeTier(4, "三等奖", "10元"),
                new PrizeTier(3, "四等奖", "3元")),
        X7 ("x7",  "选七",  7,
                new PrizeTier(7, "一等奖", "10000元"),
                new PrizeTier(6, "二等奖", "288元"),
                new PrizeTier(5, "三等奖", "28元"),
                new PrizeTier(4, "四等奖", "4元"),
                new PrizeTier(0, "五等奖", "2元")),
        X8 ("x8",  "选八",  8,
                new PrizeTier(8, "一等奖", "50000元"),
                new PrizeTier(7, "二等奖", "800元"),
                new PrizeTier(6, "三等奖", "88元"),
                new PrizeTier(5, "四等奖", "10元"),
                new PrizeTier(4, "五等奖", "3元"),
                new PrizeTier(0, "六等奖", "2元")),
        X9 ("x9",  "选九",  9,
                new PrizeTier(9, "一等奖", "浮动（约30万）"),
                new PrizeTier(8, "二等奖", "2000元"),
                new PrizeTier(7, "三等奖", "200元"),
                new PrizeTier(6, "四等奖", "20元"),
                new PrizeTier(5, "五等奖", "5元"),
                new PrizeTier(4, "六等奖", "3元"),
                new PrizeTier(0, "七等奖", "2元")),
        X10("x10", "选十", 10,
                new PrizeTier(10, "一等奖", "浮动（最高500万）"),
                new PrizeTier(9,  "二等奖", "8000元"),
                new PrizeTier(8,  "三等奖", "800元"),
                new PrizeTier(7,  "四等奖", "80元"),
                new PrizeTier(6,  "五等奖", "5元"),
                new PrizeTier(5,  "六等奖", "3元"),
                new PrizeTier(0,  "七等奖", "2元"));

        /** 玩法编码，如 "x10" */
        public final String code;
        /** 玩法中文名，如 "选十" */
        public final String name;
        /** 每注需选号码个数 */
        public final int pickCount;
        /** 奖级表（按命中数降序排列，0 表示全不中保底） */
        public final PrizeTier[] tiers;

        PlayStyle(String code, String name, int pickCount, PrizeTier... tiers) {
            this.code = code;
            this.name = name;
            this.pickCount = pickCount;
            this.tiers = tiers;
        }

        /** 根据编码查找玩法，默认返回选十 */
        static PlayStyle of(String code) {
            for (PlayStyle ps : values()) {
                if (ps.code.equals(code)) return ps;
            }
            return X10;
        }
    }

    // ==================== API 数据获取（与之前一致） ====================

    @Override
    public LotteryTypeEnum getType() {
        return LotteryTypeEnum.KL8;
    }

    @Override
    public LotteryDrawDTO fetchLatestDraw() {
        return fetchDraw(null);
    }

    @Override
    public LotteryDrawDTO fetchDrawByPeriod(String periodNo) {
        return fetchDraw(periodNo);
    }

    @Override
    public List<String> fetchRecentPeriods(int count) {
        try {
            List<JSONObject> list = fetchDrawList(count);
            return list.stream()
                    .map(o -> o.getString("code"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取快乐八期号列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private LotteryDrawDTO fetchDraw(String periodNo) {
        try {
            if (periodNo == null || periodNo.isEmpty()) {
                List<JSONObject> list = fetchDrawList(1);
                if (list.isEmpty()) throw new RuntimeException("API 返回空数据");
                return parseDrawData(list.get(0));
            }
            List<JSONObject> list = fetchDrawList(BATCH_SIZE);
            for (JSONObject item : list) {
                if (periodNo.equals(item.getString("code"))) return parseDrawData(item);
            }
            log.warn("未在最近 {} 期中找到期号 {}，返回最新一期", BATCH_SIZE, periodNo);
            return parseDrawData(list.get(0));
        } catch (Exception e) {
            log.error("获取快乐八开奖数据失败: {}", e.getMessage());
            throw new RuntimeException("开奖数据获取失败，请稍后重试", e);
        }
    }

    private List<JSONObject> fetchDrawList(int count) {
        String url = API_BASE + "&issueCount=" + count;
        String result = HttpUtil.get(url);
        JSONObject json = JSON.parseObject(result);
        if (json.getIntValue("state") != 0) {
            throw new RuntimeException("API 返回异常: " + json.getString("message"));
        }
        JSONArray arr = json.getJSONArray("result");
        if (arr == null || arr.isEmpty()) return Collections.emptyList();
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) list.add(arr.getJSONObject(i));
        return list;
    }

    /**
     * 将 API 返回的单条 JSON 解析为 LotteryDrawDTO
     * <p>对 red 字段做空值防护，避免 API 字段缺失导致 NPE。</p>
     */
    private LotteryDrawDTO parseDrawData(JSONObject data) {
        LotteryDrawDTO dto = new LotteryDrawDTO();
        dto.setPeriodNo(data.getString("code"));
        String rawDate = data.getString("date");
        dto.setDrawDate(rawDate != null ? rawDate.replaceAll("\\(.*\\)", "") : "");
        // 红球字符串 "01,02,..." —— 做空值防护
        String redStr = data.getString("red");
        if (redStr != null && !redStr.isEmpty()) {
            dto.setRedBalls(Arrays.stream(redStr.split(","))
                    .map(Integer::parseInt).collect(Collectors.toList()));
        } else {
            dto.setRedBalls(Collections.emptyList());
            log.warn("API 返回的 red 字段为空，期号={}", dto.getPeriodNo());
        }
        dto.setBlueBall(null);
        dto.setPoolAmount(data.getString("poolmoney"));

        // 解析所有奖项详情（不再只取选十）
        JSONArray prizes = data.getJSONArray("prizegrades");
        if (prizes != null) {
            List<LotteryDrawDTO.PrizeDetail> details = new ArrayList<>();
            for (int i = 0; i < prizes.size(); i++) {
                JSONObject p = prizes.getJSONObject(i);
                LotteryDrawDTO.PrizeDetail detail = new LotteryDrawDTO.PrizeDetail();
                detail.setLevel(p.getString("type"));
                detail.setCount(p.getIntValue("typenum"));
                detail.setAmount(p.getString("typemoney"));
                details.add(detail);
            }
            dto.setPrizeDetails(details);
        }
        return dto;
    }

    // ==================== 核心中奖计算（按玩法匹配奖级表） ====================

    /**
     * 快乐八中奖计算 —— 根据前端传来的 kl8PlayStyle 选择对应玩法
     * <p>比对投注号码与 20 个开奖号码的交集个数，查该玩法奖级表。</p>
     * <p>对开奖数据做空值防护，避免 API 异常返回 null 列表导致 NPE。</p>
     */
    @Override
    public List<PrizeResultDTO> calculatePrize(LotteryDrawDTO drawData, BetInputDTO betInput) {
        // 空值防护：API 异常时开奖号码列表可能为 null
        List<Integer> drawnBalls = drawData.getRedBalls();
        if (drawnBalls == null) {
            drawnBalls = Collections.emptyList();
            log.warn("开奖号码数据为空，期号={}", drawData.getPeriodNo());
        }
        PlayStyle style = PlayStyle.of(betInput.getKl8PlayStyle());
        List<PrizeResultDTO> results = new ArrayList<>();

        for (int i = 0; i < betInput.getBets().size(); i++) {
            List<Integer> bet = betInput.getBets().get(i);

            // 统计命中个数
            int matchCount = 0;
            List<Integer> matchedBalls = new ArrayList<>();
            for (Integer num : bet) {
                if (drawnBalls.contains(num)) {
                    matchCount++;
                    matchedBalls.add(num);
                }
            }

            PrizeResultDTO result = new PrizeResultDTO();
            result.setPeriodNo(drawData.getPeriodNo());
            result.setBetIndex(i + 1);
            result.setRedMatchCount(matchCount);
            result.setBlueMatch(false);
            result.setMatchedRedBalls(matchedBalls);

            // 按该玩法的奖级表从高到低匹配
            boolean found = false;
            for (PrizeTier tier : style.tiers) {
                if (tier.matchCount == matchCount
                        || (tier.matchCount == 0 && matchCount == 0)) {
                    result.setPrizeLevel(tier.level);
                    result.setPrizeAmount(tier.amount);
                    result.setWon(true);
                    result.setDescription("🎉 " + style.name + "中" + matchCount + "！恭喜中得" + tier.level + "！");
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.setPrizeLevel("未中奖");
                result.setPrizeAmount("0元");
                result.setWon(false);
                result.setDescription("很遗憾，" + style.name + "命中 " + matchCount + " 个号码，未中奖");
            }
            results.add(result);
        }
        return results;
    }

    // ==================== 规则与配置输出 ====================

    /**
     * 返回默认（选十）的中奖规则，供页面初始展示
     */
    @Override
    public Map<String, String> getPrizeRules() {
        return buildRulesMap(PlayStyle.X10);
    }

    /**
     * 返回所有 10 种玩法的中奖规则，供前端玩法切换时动态更新规则表
     *
     * @return key=玩法编码(x1~x10)，value=该玩法的规则 Map
     */
    @Override
    public Map<String, Map<String, String>> getAllPrizeRules() {
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        for (PlayStyle ps : PlayStyle.values()) {
            all.put(ps.code, buildRulesMap(ps));
        }
        return all;
    }

    /**
     * 获取全部玩法列表，供前端渲染玩法选择器
     * <p>覆盖接口默认方法，返回选一~选十共 10 种玩法。</p>
     */
    @Override
    public List<Map<String, Object>> getPlayStyles() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PlayStyle ps : PlayStyle.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", ps.code);
            item.put("name", ps.name);
            item.put("pickCount", ps.pickCount);
            list.add(item);
        }
        return list;
    }

    /** 将某个玩法的奖级表转为前端展示用的规则 Map */
    private Map<String, String> buildRulesMap(PlayStyle style) {
        Map<String, String> rules = new LinkedHashMap<>();
        for (PrizeTier tier : style.tiers) {
            String cond;
            if (tier.matchCount == 0) {
                cond = "一个都没选中（0/" + style.pickCount + "），保底奖金 " + tier.amount;
            } else {
                cond = "选中 " + tier.matchCount + " 个号码（"
                        + tier.matchCount + "/" + style.pickCount + "），奖金 " + tier.amount;
            }
            rules.put(tier.level, cond);
        }
        return rules;
    }

    @Override
    public String getBallDesc() {
        return "从 1-80 中选号，每期开出 20 个中奖号码，支持选一~选十";
    }

    /**
     * 默认号码配置（选十：10 个输入框，无蓝球，1-80，开出 20 个）
     */
    @Override
    public Map<String, Object> getBallConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("inputCount", 10);
        config.put("redInputCount", 10);
        config.put("blueInputCount", 0);
        config.put("maxRed", 80);
        config.put("maxBlue", 0);
        config.put("drawnRedCount", 20);
        config.put("drawnBlueCount", 0);
        return config;
    }
}
