// controller 包负责接收浏览器或其他客户端发来的 HTTP 请求。
package com.example.demo.controller;

// Lombok 自动为类创建名为 log 的日志对象。
import lombok.extern.slf4j.Slf4j;
// 把 Java 方法映射为 HTTP GET 接口。
import org.springframework.web.bind.annotation.GetMapping;
// 设置这个控制器的统一 URL 前缀。
import org.springframework.web.bind.annotation.RequestMapping;
// 表示方法返回值会直接写入 HTTP 响应正文。
import org.springframework.web.bind.annotation.RestController;

// 把当前类注册为 REST 控制器。
@RestController
// 自动创建日志对象。
@Slf4j
// 当前控制器的基础路径是根路径。
@RequestMapping("/")
public class HelloController {
    // 浏览器访问 GET /hello 时执行下面的方法。
    @GetMapping("/hello")
    public String hello() {
        // 在控制台记录一次接口访问。
        log.info("收到 hello 接口请求");
        // 把字符串作为 HTTP 响应返回给浏览器。
        return "hello world";
    }
}
