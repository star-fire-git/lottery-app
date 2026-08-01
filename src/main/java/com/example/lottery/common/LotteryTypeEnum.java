package com.example.lottery.common;

import lombok.Getter;

/**
 * 彩票类型枚举 —— 系统的扩展入口
 * <p>
 * 每种彩票对应一个枚举值，包含编码、中文名、前端页面路径。
 * 新增彩种时只需在此添加枚举值，配合新增对应的 {@code Strategy} 实现类即可完成扩展。
 * </p>
 *
 * <h3>扩展示例</h3>
 * <pre>{@code
 * DLT("DLT", "大乐透", "/dlt"),
 * QXC("QXC", "七星彩", "/qxc"),
 * }</pre>
 */
@Getter
public enum LotteryTypeEnum {

    /** 双色球 */
    SSQ("SSQ", "双色球", "/ssq"),

    /** 快乐八（选十玩法） */
    KL8("KL8", "快乐八", "/kl8");

    /** 彩票编码，用于 API 请求参数和策略匹配 */
    private final String code;

    /** 彩票中文名称，用于前端导航展示 */
    private final String name;

    /** 前端页面路径，用于 Thymeleaf 路由跳转 */
    private final String pagePath;

    LotteryTypeEnum(String code, String name, String pagePath) {
        this.code = code;
        this.name = name;
        this.pagePath = pagePath;
    }
}
