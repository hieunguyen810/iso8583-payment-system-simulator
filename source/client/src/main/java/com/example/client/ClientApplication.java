package com.example.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        System.out.println("🔄 Starting ISO 8583 Client...");
        SpringApplication.run(ClientApplication.class, args);
    }
}