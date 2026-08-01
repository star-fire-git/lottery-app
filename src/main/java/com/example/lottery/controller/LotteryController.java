package com.example.lottery.controller;

import com.example.lottery.common.LotteryTypeEnum;
import com.example.lottery.config.LotteryConfig;
import com.example.lottery.dto.BetInputDTO;
import com.example.lottery.dto.LotteryDrawDTO;
import com.example.lottery.dto.PrizeResultDTO;
import com.example.lottery.service.LotteryDataService;
import com.example.lottery.service.PrizeCalcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 彩票控制器 —— 页面路由与 REST API
 * <p>
 * 负责：
 * <ul>
 *   <li>页面路由：/ → 重定向到默认彩种页，/ssq → 双色球主页，/kl8 → 快乐八主页</li>
 *   <li>REST API：开奖数据查询、中奖计算、规则查询、期号列表</li>
 * </ul>
 * </p>
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class LotteryController {

    private final LotteryDataService lotteryDataService;
    private final PrizeCalcService prizeCalcService;
    private final LotteryConfig lotteryConfig;

    // ==================== 页面路由 ====================

    /**
     * 根路径，重定向到默认彩种（双色球）页面
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/ssq";
    }

    /**
     * 双色球主页
     * <p>向 Model 注入开奖数据、玩法规则、期号列表、彩票类型列表等</p>
     */
    @GetMapping("/ssq")
    public String ssqPage(Model model) {
        return buildLotteryPage(LotteryTypeEnum.SSQ, model);
    }

    /**
     * 快乐八主页 —— 支持选一至选十全部玩法
     * <p>与双色球共用同一模板 lottery.html，通过 ballConfig 动态适配号码结构。</p>
     */
    @GetMapping("/kl8")
    public String kl8Page(Model model) {
        return buildLotteryPage(LotteryTypeEnum.KL8, model);
    }

    /**
     * 构建彩种页面通用 Model 数据
     * <p>抽取 SSQ 和 KL8 的公共逻辑，避免重复代码。</p>
     */
    private String buildLotteryPage(LotteryTypeEnum type, Model model) {
        var drawData = lotteryDataService.getLatestDraw(type);
        var strategy = lotteryConfig.getStrategy(type).orElseThrow();
        List<String> periods = lotteryDataService.getRecentPeriods(type, 30);

        model.addAttribute("drawData", drawData);
        model.addAttribute("rules", strategy.getPrizeRules());
        model.addAttribute("ballDesc", strategy.getBallDesc());
        model.addAttribute("ballConfig", strategy.getBallConfig());
        model.addAttribute("lotteryTypes", LotteryTypeEnum.values());
        model.addAttribute("currentType", type);
        model.addAttribute("periods", periods);

        // 玩法列表（KL8 有选一~选十，SSQ 为空列表）
        model.addAttribute("playStyles", strategy.getPlayStyles());
        // 全部玩法规则（KL8 返回完整映射，SSQ 返回 null，前端据此判断是否启用玩法切换）
        model.addAttribute("kl8AllRules", strategy.getAllPrizeRules());

        return "lottery";
    }

    // ==================== REST API ====================

    /**
     * 安全解析彩票类型枚举，避免用户传入非法值导致 500
     */
    private LotteryTypeEnum parseTypeEnum(String type) {
        try {
            return LotteryTypeEnum.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的彩票类型: " + type + "，可选值: "
                    + Arrays.toString(LotteryTypeEnum.values()));
        }
    }

    /**
     * 获取最新开奖数据
     *
     * @param type 彩票类型编码，默认 SSQ
     * @return JSON 格式开奖数据
     */
    @GetMapping("/api/lottery/latest")
    @ResponseBody
    public Map<String, Object> getLatestDraw(@RequestParam(defaultValue = "SSQ") String type) {
        try {
            LotteryTypeEnum typeEnum = parseTypeEnum(type);
            var drawData = lotteryDataService.getLatestDraw(typeEnum);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 0);
            result.put("data", drawData);
            return result;
        } catch (Exception e) {
            log.error("获取最新开奖数据失败: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 1);
            result.put("msg", e.getMessage());
            return result;
        }
    }

    /**
     * 根据期号获取开奖数据
     *
     * @param type     彩票类型编码
     * @param periodNo 期号
     * @return JSON 格式开奖数据
     */
    @GetMapping("/api/lottery/draw")
    @ResponseBody
    public Map<String, Object> getDrawByPeriod(
            @RequestParam(defaultValue = "SSQ") String type,
            @RequestParam String periodNo) {
        try {
            LotteryTypeEnum typeEnum = parseTypeEnum(type);
            var drawData = lotteryDataService.getDrawByPeriod(typeEnum, periodNo);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 0);
            result.put("data", drawData);
            return result;
        } catch (Exception e) {
            log.error("按期号获取开奖数据失败: type={}, periodNo={}, error={}", type, periodNo, e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 1);
            result.put("msg", e.getMessage());
            return result;
        }
    }

    /**
     * 中奖计算
     * <p>支持两种模式：
     * <ul>
     *   <li>API 模式：后端根据 periodNo 拉取开奖数据后计算</li>
     *   <li>手动模式：前端传入 manualRedBalls/manualBlueBall，直接使用用户输入的开奖号码计算</li>
     * </ul></p>
     *
     * @param betInput 用户投注输入 JSON
     * @return 中奖结果列表
     */
    @PostMapping("/api/lottery/check")
    @ResponseBody
    public Map<String, Object> checkPrize(@RequestBody BetInputDTO betInput) {
        try {
            LotteryTypeEnum typeEnum = parseTypeEnum(betInput.getLotteryType());
            List<PrizeResultDTO> results;

            if (betInput.isManualMode()) {
                // 手动模式：直接使用前端传入的开奖号码
                LotteryDrawDTO drawData = betInput.toManualDrawDTO();
                results = prizeCalcService.calculateWithManualData(typeEnum, drawData, betInput);
            } else {
                // API 模式：后端拉取开奖数据后计算
                results = prizeCalcService.calculate(typeEnum, betInput.getPeriodNo(), betInput);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 0);
            result.put("data", results);
            return result;
        } catch (Exception e) {
            log.error("中奖计算失败: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 1);
            result.put("msg", "计算失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 获取玩法规则
     *
     * @param type 彩票类型编码
     * @return 规则映射表
     */
    @GetMapping("/api/lottery/rules")
    @ResponseBody
    public Map<String, Object> getRules(@RequestParam(defaultValue = "SSQ") String type) {
        try {
            LotteryTypeEnum typeEnum = parseTypeEnum(type);
            var strategy = lotteryConfig.getStrategy(typeEnum).orElseThrow();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 0);
            result.put("data", strategy.getPrizeRules());
            result.put("ballDesc", strategy.getBallDesc());
            return result;
        } catch (Exception e) {
            log.error("获取规则失败: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 1);
            result.put("msg", e.getMessage());
            return result;
        }
    }

    /**
     * 获取近 N 期期号列表，供前端下拉选择
     *
     * @param type  彩票类型编码
     * @param count 期数，默认 30，最大 200
     * @return 期号列表
     */
    @GetMapping("/api/lottery/periods")
    @ResponseBody
    public Map<String, Object> getPeriods(
            @RequestParam(defaultValue = "SSQ") String type,
            @RequestParam(defaultValue = "30") int count) {
        try {
            LotteryTypeEnum typeEnum = parseTypeEnum(type);
            // 限制最大期数，防止恶意请求
            int safeCount = Math.min(count, 200);
            List<String> periods = lotteryDataService.getRecentPeriods(typeEnum, safeCount);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 0);
            result.put("data", periods);
            return result;
        } catch (Exception e) {
            log.error("获取期号列表失败: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 1);
            result.put("msg", e.getMessage());
            return result;
        }
    }

}
