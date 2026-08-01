package com.example.lottery.config;

import com.example.lottery.common.LotteryTypeEnum;
import com.example.lottery.strategy.LotteryStrategy;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 彩票策略注册中心 —— 利用 Spring 自动注入实现策略 Map
 * <p>
 * 核心机制：通过构造器注入 {@code List<LotteryStrategy>}，
 * Spring 会自动收集所有标注了 {@code @Component} 的策略实现类，
 * 然后按 {@link LotteryStrategy#getType()} 构建
 * {@code Map<LotteryTypeEnum, LotteryStrategy>} 的映射关系。
 * </p>
 * <p>
 * <b>扩展性：</b>新增彩种策略只需写一个 {@code @Component} 实现类，
 * 无需修改本类任何代码，Spring 会自动将其收录进策略地图。
 * </p>
 */
@Configuration
public class LotteryConfig {

    /** 策略映射表，key=彩票类型枚举，value=对应的策略实现 */
    private final Map<LotteryTypeEnum, LotteryStrategy> strategyMap;

    /**
     * 构造器注入 —— Spring 自动收集所有 LotteryStrategy 实现
     *
     * @param strategies Spring 容器中所有 LotteryStrategy 的 Bean 列表
     */
    public LotteryConfig(List<LotteryStrategy> strategies) {
        // 使用 merge 函数防止同一类型出现多个实现时抛 IllegalStateException
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        LotteryStrategy::getType,
                        Function.identity(),
                        (existing, replacement) -> {
                            // 同一类型多个实现时保留第一个，记录警告
                            System.err.println("警告：彩票类型 " + existing.getType()
                                    + " 存在多个策略实现，将使用第一个注册的: "
                                    + existing.getClass().getSimpleName());
                            return existing;
                        }
                ));
    }

    /**
     * 根据彩票类型获取对应的策略实现
     *
     * @param type 彩票类型枚举
     * @return Optional 包装的策略对象，不存在时为 Optional.empty()
     */
    public Optional<LotteryStrategy> getStrategy(LotteryTypeEnum type) {
        return Optional.ofNullable(strategyMap.get(type));
    }

    /**
     * 获取完整的策略映射表，供前端遍历所有支持的彩种
     *
     * @return 不可变的策略 Map
     */
    public Map<LotteryTypeEnum, LotteryStrategy> getStrategyMap() {
        return strategyMap;
    }
}
