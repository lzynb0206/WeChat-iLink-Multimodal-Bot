package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/")
public class HelloController {
    @GetMapping("/hello")
    public String hello(){
        log.info("收到hello接口请求");
        return "hello world";
    }
}
