package com.example.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CommonApplication {

    public static void main(String[] args) {
        System.out.println("📦 Starting ISO 8583 Common Module...");
        SpringApplication.run(CommonApplication.class, args);
    }
}