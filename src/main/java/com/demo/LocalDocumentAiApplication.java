package com.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LocalDocumentAiApplication {
    public static void main(String[] args) {
        // 这一行是关键！必须调用 SpringApplication.run 才能启动Web服务
        SpringApplication.run(LocalDocumentAiApplication.class, args);

        System.out.println("========================================");
        System.out.println("🤖 本地文档AI智能体 Web版已启动");
        System.out.println("🌐 访问地址: http://localhost:8080");
        System.out.println("========================================");
    }
}
