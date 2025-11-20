package com.example.simulator.config;

import io.grpc.LoadBalancerRegistry;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import io.grpc.util.RoundRobinLoadBalancerProvider;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class GrpcClientConfig {
    
    @PostConstruct
    public void configureLoadBalancer() {
        LoadBalancerRegistry.getDefaultRegistry().register(new RoundRobinLoadBalancerProvider());
    }
}