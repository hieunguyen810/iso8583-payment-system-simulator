package com.example.simulator.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {
    // Load balancer configuration is now handled automatically by gRPC
    // No manual registration needed for standard load balancers
}