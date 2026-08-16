package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@RestController
public class CmdController {

    // 项目版本，从application.yml读取
    @Value("${project.version:1.0.0}")
    private String projectVersion;

    // 记录程序启动时刻
    private final LocalDateTime bootTime = LocalDateTime.now();

    @GetMapping("/help")
    public String help(){
        return """
                可用命令列表：
                /help      查看命令列表
                /version   查看项目版本
                /status    查看运行状态
                """;
    }

    @GetMapping("/version")
    public String version(){
        return "项目版本号："+projectVersion;
    }

    @GetMapping("/status")
    public String status(){
        Duration run = Duration.between(bootTime,LocalDateTime.now());
        return String.format("当前时间：%s，已运行时长：%d时%d分%d秒",
                LocalDateTime.now(),
                run.toHoursPart(),
                run.toMinutesPart(),
                run.toSecondsPart());
    }
}