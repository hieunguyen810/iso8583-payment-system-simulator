package com.example.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {

    public static void main(String[] args) {
        // Check if database writes should be disabled via environment or system property
        String dbWriteEnabled = System.getProperty("iso8583.database.write.enabled", 
            System.getenv().getOrDefault("ISO8583_DATABASE_WRITE_ENABLED", "true"));
        
        if ("false".equals(dbWriteEnabled)) {
            System.out.println("🚫 Database writes disabled - starting ISO 8583 Server without JPA");
            SpringApplication.run(ServerApplicationNoDb.class, args);
        } else {
            System.out.println("🚀 Starting ISO 8583 Server with database support...");
            SpringApplication.run(ServerApplication.class, args);
        }
    }
}