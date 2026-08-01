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
 * 双色球（SSQ）策略实现
 * <p>
 * 通过中国福利彩票官方 API 获取真实开奖数据。
 * API 不支持按单期查询，采用「批量拉取 + 内存匹配」策略。
 * </p>
 *
 * <h3>双色球玩法</h3>
 * <p>红球：从 1-33 中选 6 个（不可重复），蓝球：从 1-16 中选 1 个。</p>
 */
@Slf4j
@Component
public class SsqStrategy implements LotteryStrategy {

    /** 中国福利彩票开奖查询 API */
    private static final String API_BASE =
            "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=ssq";

    /** 按期号查询时拉取的批量期数，在内存中匹配目标期号 */
    private static final int BATCH_SIZE = 100;

    @Override
    public LotteryTypeEnum getType() {
        return LotteryTypeEnum.SSQ;
    }

    @Override
    public LotteryDrawDTO fetchLatestDraw() {
        return fetchDraw(null);
    }

    @Override
    public LotteryDrawDTO fetchDrawByPeriod(String periodNo) {
        return fetchDraw(periodNo);
    }

    /**
     * 获取近 N 期期号列表，供前端下拉选择
     * <p>直接从 API 拉取最新 count 期数据并提取期号。</p>
     */
    @Override
    public List<String> fetchRecentPeriods(int count) {
        try {
            List<JSONObject> list = fetchDrawList(count);
            return list.stream()
                    .map(o -> o.getString("code"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取期号列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 核心数据获取：按期号查询或取最新一期
     * <p>
     * 由于官方 API 不支持 issueNum 参数，指定期号时采用策略：
     * 拉取最近 BATCH_SIZE（100）期数据，在内存中按 periodNo 精确匹配。
     * </p>
     *
     * @param periodNo 期号，为 null 时取最新一期
     */
    private LotteryDrawDTO fetchDraw(String periodNo) {
        try {
            if (periodNo == null || periodNo.isEmpty()) {
                // 最新一期：只拉 1 条
                List<JSONObject> list = fetchDrawList(1);
                if (list.isEmpty()) {
                    throw new RuntimeException("API 返回空数据");
                }
                return parseDrawData(list.get(0));
            }

            // 指定期号：批量拉取后内存匹配
            List<JSONObject> list = fetchDrawList(BATCH_SIZE);
            for (JSONObject item : list) {
                if (periodNo.equals(item.getString("code"))) {
                    return parseDrawData(item);
                }
            }
            // 没匹配到 —— 可能是太老的期号，返回最新一期
            log.warn("未在最近 {} 期中找到期号 {}，返回最新一期", BATCH_SIZE, periodNo);
            return parseDrawData(list.get(0));

        } catch (Exception e) {
            log.error("获取双色球开奖数据失败: {}", e.getMessage());
            throw new RuntimeException("开奖数据获取失败，请稍后重试", e);
        }
    }

    /**
     * 调用官方 API 拉取指定数量的开奖记录
     */
    private List<JSONObject> fetchDrawList(int count) {
        String url = API_BASE + "&issueCount=" + count;
        String result = HttpUtil.get(url);
        JSONObject json = JSON.parseObject(result);

        if (json.getIntValue("state") != 0) {
            throw new RuntimeException("API 返回异常: " + json.getString("message"));
        }

        JSONArray arr = json.getJSONArray("result");
        if (arr == null || arr.isEmpty()) {
            return Collections.emptyList();
        }

        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            list.add(arr.getJSONObject(i));
        }
        return list;
    }

    /**
     * 将 API 返回的单条 JSON 解析为 LotteryDrawDTO
     * <p>对 red、blue、prizegrades 等关键字段做空值防护，避免 API 字段缺失导致 NPE。</p>
     */
    private LotteryDrawDTO parseDrawData(JSONObject data) {
        LotteryDrawDTO dto = new LotteryDrawDTO();
        dto.setPeriodNo(data.getString("code"));
        // 日期格式 "2026-07-30(四)" → 去掉星期
        String rawDate = data.getString("date");
        dto.setDrawDate(rawDate != null ? rawDate.replaceAll("\\(.*\\)", "") : "");

        // 红球："04,06,10,18,23,31" —— 做空值防护
        String redStr = data.getString("red");
        if (redStr != null && !redStr.isEmpty()) {
            dto.setRedBalls(Arrays.stream(redStr.split(","))
                    .map(Integer::parseInt)
                    .collect(Collectors.toList()));
        } else {
            dto.setRedBalls(Collections.emptyList());
            log.warn("API 返回的 red 字段为空，期号={}", dto.getPeriodNo());
        }

        // 蓝球 —— 做空值防护
        String blueStr = data.getString("blue");
        if (blueStr != null && !blueStr.isEmpty()) {
            dto.setBlueBall(Integer.parseInt(blueStr));
        } else {
            dto.setBlueBall(null);
            log.warn("API 返回的 blue 字段为空，期号={}", dto.getPeriodNo());
        }

        dto.setPoolAmount(data.getString("poolmoney"));

        // 各等级中奖详情
        JSONArray prizes = data.getJSONArray("prizegrades");
        if (prizes != null) {
            List<LotteryDrawDTO.PrizeDetail> details = new ArrayList<>();
            String[] levelNames = {"", "一等奖", "二等奖", "三等奖", "四等奖", "五等奖", "六等奖"};
            for (int i = 0; i < prizes.size(); i++) {
                JSONObject p = prizes.getJSONObject(i);
                int type = p.getIntValue("type");
                LotteryDrawDTO.PrizeDetail detail = new LotteryDrawDTO.PrizeDetail();
                // 防止数组越界：type 超出范围时使用 API 原始 typeName 兜底
                if (type > 0 && type < levelNames.length) {
                    detail.setLevel(levelNames[type]);
                } else {
                    detail.setLevel(p.getString("typeName") != null
                            ? p.getString("typeName") : "其他");
                }
                detail.setCount(p.getIntValue("typenum"));
                detail.setAmount(p.getString("typemoney"));
                details.add(detail);
            }
            dto.setPrizeDetails(details);
        }
        return dto;
    }

    // ==================== 以下方法不变 ====================

    /**
     * 双色球中奖计算核心算法
     * <p>对开奖数据做空值防护，避免 API 异常返回 null 列表导致 NPE。</p>
     */
    @Override
    public List<PrizeResultDTO> calculatePrize(LotteryDrawDTO drawData, BetInputDTO betInput) {
        // 空值防护：API 异常时红球列表可能为 null
        List<Integer> drawReds = drawData.getRedBalls();
        if (drawReds == null) {
            drawReds = Collections.emptyList();
            log.warn("开奖红球数据为空，期号={}", drawData.getPeriodNo());
        }
        Integer drawBlue = drawData.getBlueBall();
        List<PrizeResultDTO> results = new ArrayList<>();

        for (int i = 0; i < betInput.getBets().size(); i++) {
            List<Integer> bet = betInput.getBets().get(i);
            List<Integer> betReds = bet.subList(0, Math.min(6, bet.size()));
            Integer betBlue = bet.size() >= 7 ? bet.get(6) : null;

            int redMatch = 0;
            List<Integer> matchedReds = new ArrayList<>();
            for (Integer r : betReds) {
                if (drawReds.contains(r)) {
                    redMatch++;
                    matchedReds.add(r);
                }
            }
            boolean blueMatch = betBlue != null && betBlue.equals(drawBlue);

            PrizeResultDTO result = new PrizeResultDTO();
            result.setPeriodNo(drawData.getPeriodNo());
            result.setBetIndex(i + 1);
            result.setRedMatchCount(redMatch);
            result.setBlueMatch(blueMatch);
            result.setMatchedRedBalls(matchedReds);

            if (redMatch == 6 && blueMatch) {
                result.setPrizeLevel("一等奖");
                result.setPrizeAmount("浮动（最高1000万）");
                result.setWon(true);
                result.setDescription("恭喜中得一等奖！");
            } else if (redMatch == 6 && !blueMatch) {
                result.setPrizeLevel("二等奖");
                result.setPrizeAmount("浮动");
                result.setWon(true);
                result.setDescription("恭喜中得二等奖！");
            } else if (redMatch == 5 && blueMatch) {
                result.setPrizeLevel("三等奖");
                result.setPrizeAmount("3000元");
                result.setWon(true);
                result.setDescription("恭喜中得三等奖！");
            } else if ((redMatch == 5 && !blueMatch) || (redMatch == 4 && blueMatch)) {
                result.setPrizeLevel("四等奖");
                result.setPrizeAmount("200元");
                result.setWon(true);
                result.setDescription("恭喜中得四等奖！");
            } else if ((redMatch == 4 && !blueMatch) || (redMatch == 3 && blueMatch)) {
                result.setPrizeLevel("五等奖");
                result.setPrizeAmount("10元");
                result.setWon(true);
                result.setDescription("恭喜中得五等奖！");
            } else if (blueMatch) {
                result.setPrizeLevel("六等奖");
                result.setPrizeAmount("5元");
                result.setWon(true);
                result.setDescription("恭喜中得六等奖！");
            } else {
                result.setPrizeLevel("未中奖");
                result.setPrizeAmount("0元");
                result.setWon(false);
                result.setDescription("很遗憾，未中奖");
            }
            results.add(result);
        }
        return results;
    }

    @Override
    public Map<String, String> getPrizeRules() {
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("一等奖", "红球 6/6 + 蓝球 1/1，奖金浮动（最高1000万）");
        rules.put("二等奖", "红球 6/6 + 蓝球 0/1，奖金浮动");
        rules.put("三等奖", "红球 5/6 + 蓝球 1/1，固定奖金 3000元");
        rules.put("四等奖", "红球 5/6 或 4/6 + 蓝球 1/1，固定奖金 200元");
        rules.put("五等奖", "红球 4/6 或 3/6 + 蓝球 1/1，固定奖金 10元");
        rules.put("六等奖", "蓝球 1/1（红球 0~2个），固定奖金 5元");
        return rules;
    }

    @Override
    public String getBallDesc() {
        return "红球 1-33 选 6 个，蓝球 1-16 选 1 个";
    }

    /**
     * 双色球为单玩法彩种，不提供多玩法规则映射
     */
    @Override
    public Map<String, Map<String, String>> getAllPrizeRules() {
        return null;
    }

    /** 双色球号码配置：6 红 + 1 蓝，红球 1-33，蓝球 1-16 */
    @Override
    public Map<String, Object> getBallConfig() {
        return LotteryStrategy.super.getBallConfig();
    }

}
