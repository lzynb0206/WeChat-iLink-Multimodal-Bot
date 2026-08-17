// 声明当前类所在的基础包；Spring 会从这个包向下扫描 controller、config 和 service。
package com.example.demo;

// 导入 Spring Boot 的应用启动工具类。
import org.springframework.boot.SpringApplication;
// 导入 Spring Boot 的核心注解，用于开启自动配置和组件扫描。
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 标记这是整个 Spring Boot 项目的启动类。
@SpringBootApplication
public class DemoApplication {

    // Java 程序从 main 方法开始运行。
    public static void main(String[] args) {
        // 创建 Spring 容器、加载配置并启动内置的 Tomcat Web 服务器。
        SpringApplication.run(DemoApplication.class, args);
    }
}
