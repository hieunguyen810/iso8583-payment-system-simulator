package com.example.simulator.service;

import com.example.common.model.Iso8583Message;
import com.example.simulator.config.SimulatorConfig;
import com.example.simulator.grpc.Iso8583Proto;
import com.example.simulator.grpc.Iso8583ServiceGrpc;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TransactionSimulatorService {

    @GrpcClient("iso8583-server")
    private Iso8583ServiceGrpc.Iso8583ServiceBlockingStub iso8583ServiceStub;

    @Autowired
    private SimulatorConfig config;
    
    @Autowired
    private Tracer tracer;

    private final Random random = new Random();
    private final AtomicInteger stanCounter = new AtomicInteger(1);
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final AtomicLong successfulTransactions = new AtomicLong(0);
    private final AtomicLong failedTransactions = new AtomicLong(0);
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private volatile boolean spikeActive = false;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    @EventListener(ApplicationReadyEvent.class)
    public void startSimulation() {
        if (!config.isEnabled()) {
            System.out.println("🚫 Simulator disabled");
            return;
        }
        
        System.out.println("🚀 Starting simulator in " + config.getMode() + " mode");
        
        switch (config.getMode()) {
            case LOAD_TEST -> startLoadTest();
            case SPIKE -> startSpikeTest();
            case MANUAL -> System.out.println("📋 Manual mode - use REST endpoints to trigger transactions");
            default -> System.out.println("⏰ Scheduled mode active");
        }
    }
    
    @Scheduled(fixedRateString = "#{simulatorConfig.scheduled.intervalMs}")
    public void scheduledTransaction() {
        if (!config.isEnabled() || config.getMode() != SimulatorConfig.Mode.SCHEDULED) {
            return;
        }
        sendTransaction();
    }
    
    @Async
    public void startLoadTest() {
        var loadConfig = config.getLoadTest();
        System.out.println("🔥 Starting load test: " + loadConfig.getThreadsPerSecond() + " TPS for " + loadConfig.getDurationSeconds() + "s");
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (loadConfig.getDurationSeconds() * 1000L);
        
        scheduler.scheduleAtFixedRate(() -> {
            if (System.currentTimeMillis() > endTime) {
                scheduler.shutdown();
                printStats();
                return;
            }
            
            for (int i = 0; i < loadConfig.getThreadsPerSecond(); i++) {
                executorService.submit(this::sendTransaction);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    @Async
    public void startSpikeTest() {
        var spikeConfig = config.getSpike();
        System.out.println("⚡ Starting spike test: Normal " + spikeConfig.getNormalTps() + " TPS, Spike " + spikeConfig.getSpikeTps() + " TPS");
        
        // Normal load
        scheduler.scheduleAtFixedRate(() -> {
            if (!spikeActive) {
                for (int i = 0; i < spikeConfig.getNormalTps(); i++) {
                    executorService.submit(this::sendTransaction);
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
        
        // Spike scheduler
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("🌋 Starting spike for " + spikeConfig.getSpikeDurationSeconds() + " seconds");
            spikeActive = true;
            
            ScheduledFuture<?> spikeTask = scheduler.scheduleAtFixedRate(() -> {
                for (int i = 0; i < spikeConfig.getSpikeTps(); i++) {
                    executorService.submit(this::sendTransaction);
                }
            }, 0, 1, TimeUnit.SECONDS);
            
            scheduler.schedule(() -> {
                spikeTask.cancel(false);
                spikeActive = false;
                System.out.println("🌋 Spike ended");
            }, spikeConfig.getSpikeDurationSeconds(), TimeUnit.SECONDS);
            
        }, spikeConfig.getIntervalBetweenSpikesSeconds(), spikeConfig.getIntervalBetweenSpikesSeconds(), TimeUnit.SECONDS);
    }
    
    public void sendTransaction() {
        Iso8583Message transaction = createRandomTransaction();
        String stan = transaction.getField(11);
        
        Span span = tracer.spanBuilder("iso8583.simulator.send_transaction")
                .setAttribute("iso8583.stan", stan)
                .setAttribute("iso8583.correlation_id", stan)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            totalTransactions.incrementAndGet();
            
            for (int attempt = 1; attempt <= config.getScheduled().getMaxRetries(); attempt++) {
                try {
                    String message = transaction.toString();
                    
                    span.setAttribute("transaction.mti", transaction.getMti())
                        .setAttribute("transaction.stan", stan)
                        .setAttribute("transaction.amount", transaction.getField(4))
                        .setAttribute("attempt", attempt);
                
                Iso8583Proto.TransactionRequest request = Iso8583Proto.TransactionRequest.newBuilder()
                        .setMessage(message)
                        .setClientId("simulator-" + System.currentTimeMillis())
                        .build();
                
                Iso8583Proto.TransactionResponse response = iso8583ServiceStub
                        .withDeadlineAfter(5, TimeUnit.SECONDS)
                        .sendTransaction(request);
                
                    if (response.getSuccess()) {
                        successfulTransactions.incrementAndGet();
                        span.setStatus(StatusCode.OK);
                        if (config.getMode() == SimulatorConfig.Mode.SCHEDULED) {
                            System.out.println("✅ Transaction sent successfully");
                        }
                    } else {
                        failedTransactions.incrementAndGet();
                        span.setStatus(StatusCode.ERROR, "Transaction failed: " + response.getMessage());
                        System.err.println("❌ Transaction failed: " + response.getMessage());
                    }
                    return;
                
                } catch (Exception e) {
                    failedTransactions.incrementAndGet();
                    span.setStatus(StatusCode.ERROR, "Exception: " + e.getMessage());
                    if (attempt < config.getScheduled().getMaxRetries()) {
                        try {
                            Thread.sleep(config.getScheduled().getRetryDelayMs());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
    
    @Scheduled(fixedRate = 30000)
    public void printStats() {
        if (config.getMode() != SimulatorConfig.Mode.SCHEDULED) {
            long total = totalTransactions.get();
            long success = successfulTransactions.get();
            long failed = failedTransactions.get();
            double successRate = total > 0 ? (success * 100.0 / total) : 0;
            
            System.out.println(String.format("📊 Stats - Total: %d, Success: %d (%.1f%%), Failed: %d", 
                total, success, successRate, failed));
        }
    }

    private Iso8583Message createRandomTransaction() {
        Iso8583Message msg = new Iso8583Message();
        msg.setMti("0200");
        
        msg.addField(2, generatePan());
        msg.addField(3, "000000");
        msg.addField(4, String.format("%012d", random.nextInt(100000) + 1000));
        msg.addField(7, LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss")));
        msg.addField(11, String.format("%06d", stanCounter.getAndIncrement()));
        msg.addField(12, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")));
        msg.addField(13, LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd")));
        msg.addField(18, "5999");
        msg.addField(22, "012");
        msg.addField(25, "00");
        msg.addField(37, String.format("%012d", random.nextInt(999999999)));
        msg.addField(41, "SIM001  ");
        msg.addField(42, "SIMULATOR000001");
        msg.addField(49, "840");
        
        return msg;
    }

    private String generatePan() {
        return "4000" + String.format("%012d", random.nextInt(1000000000));
    }
}