// 这个包专门存放项目配置类。
package com.example.demo.config;

// Lombok 的 @Data 会自动生成 getter、setter 和 toString 等方法。
import lombok.Data;
// 该注解把 application.yaml 中指定前缀的配置绑定到 Java 字段。
import org.springframework.boot.context.properties.ConfigurationProperties;
// 该注解让 Spring 自动创建并管理这个配置对象。
import org.springframework.stereotype.Component;

// 把 LlmConfig 注册为 Spring Bean，其他类可以通过构造器使用它。
@Component
// 读取 application.yaml 中所有以 llm 开头的配置。
@ConfigurationProperties(prefix = "llm")
// 自动生成字段的 getter 和 setter，配置绑定时需要 setter。
@Data
public class LlmConfig {
    // DeepSeek Chat Completions 接口地址。
    private String apiUrl;
    // DeepSeek API Key；真实值只放在 application-local.yml 或环境变量中。
    private String apiKey;
    // DeepSeek 模型名称；没有额外配置时使用 flash 模型。
    private String model = "deepseek-v4-flash";
}
