package com.example.lottery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 彩票中奖查询系统 - Spring Boot 启动类
 * <p>
 * 通过 {@link SpringBootApplication} 自动扫描 com.example.lottery 包及其子包下的所有组件，
 * 包括 Controller、Service、Strategy、Config 等。
 * </p>
 *
 * @author lottery-team
 */
@SpringBootApplication
@Slf4j
public class LotteryAppApplication {

    /**
     * 应用程序入口，启动内嵌 Tomcat 并初始化 Spring 容器
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LotteryAppApplication.class, args);
        log.info("Lottery App Started Success...");
    }
}
