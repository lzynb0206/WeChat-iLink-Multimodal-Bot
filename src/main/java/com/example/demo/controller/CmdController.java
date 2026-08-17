// controller 包负责提供浏览器可以访问的 HTTP 接口。
package com.example.demo.controller;

// 从 application.yaml 中读取单个配置值。
import org.springframework.beans.factory.annotation.Value;
// 把方法映射为 HTTP GET 接口。
import org.springframework.web.bind.annotation.GetMapping;
// 表示返回值直接作为 HTTP 响应正文。
import org.springframework.web.bind.annotation.RestController;

// Duration 用于计算两个时间之间相隔多久。
import java.time.Duration;
// LocalDateTime 表示不带时区的日期和时间。
import java.time.LocalDateTime;

// 把这个类注册为 Spring REST 控制器。
@RestController
public class CmdController {

    // 从 project.version 读取版本号；没有配置时使用 1.0.0。
    @Value("${project.version:1.0.0}")
    private String projectVersion;

    // 创建控制器时记录启动时间，后面用它计算运行时长。
    private final LocalDateTime bootTime = LocalDateTime.now();

    // 浏览器访问 GET /help 时执行该方法。
    @GetMapping("/help")
    public String help() {
        // Java 文本块可以方便地返回多行字符串。
        return """
                可用命令列表：
                /help      查看命令列表
                /version   查看项目版本
                /status    查看运行状态
                """;
    }

    // 浏览器访问 GET /version 时执行该方法。
    @GetMapping("/version")
    public String version() {
        // 拼接并返回当前项目版本号。
        return "项目版本号：" + projectVersion;
    }

    // 浏览器访问 GET /status 时执行该方法。
    @GetMapping("/status")
    public String status() {
        // 用当前时间减去启动时间，得到已经运行的时长。
        Duration run = Duration.between(bootTime, LocalDateTime.now());
        // 把当前时间和运行时长格式化成容易阅读的文字。
        return String.format("当前时间：%s，已运行时长：%d时%d分%d秒",
                // 第一个占位符 %s 使用当前时间。
                LocalDateTime.now(),
                // 第一个 %d 使用总小时数中的小时部分。
                run.toHoursPart(),
                // 第二个 %d 使用分钟部分。
                run.toMinutesPart(),
                // 第三个 %d 使用秒数部分。
                run.toSecondsPart());
    }
}
