package com.chainsettle.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FabricGatewayConfig {
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService fabricEventExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chainsettle-fabric-events");
            thread.setDaemon(true);
            return thread;
        });
    }
}

