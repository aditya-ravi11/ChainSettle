package com.chainsettle;

import com.chainsettle.config.FabricGatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(FabricGatewayProperties.class)
public class ChainSettleApplication {
    public static void main(final String[] args) {
        SpringApplication.run(ChainSettleApplication.class, args);
    }
}

