// 测试类放在与启动类相同的基础包中，方便 Spring 找到 DemoApplication。
package com.example.demo;

// @Test 标记一个 JUnit 测试方法。
import org.junit.jupiter.api.Test;
// @SpringBootTest 会启动完整的 Spring 容器进行集成测试。
import org.springframework.boot.test.context.SpringBootTest;

// 测试时关闭微信机器人，避免自动联网和等待扫码。
@SpringBootTest(properties = "wechat.bot.enabled=false")
class DemoApplicationTests {

    // 标记下面的方法是一个测试用例。
    @Test
    void contextLoads() {
        // 方法无需写代码；只要 Spring 容器能成功启动，测试就通过。
    }
}
