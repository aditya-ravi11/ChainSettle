package com.chainsettle.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsettle.fabric")
public class FabricGatewayProperties {
    private String configBase = ".";
    private String channelName = "settlement-channel";
    private String chaincodeName = "token-settlement";
    private Map<String, String> profiles = new LinkedHashMap<>();

    public String getConfigBase() {
        return configBase;
    }

    public void setConfigBase(final String configBase) {
        this.configBase = configBase;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(final String channelName) {
        this.channelName = channelName;
    }

    public String getChaincodeName() {
        return chaincodeName;
    }

    public void setChaincodeName(final String chaincodeName) {
        this.chaincodeName = chaincodeName;
    }

    public Map<String, String> getProfiles() {
        return profiles;
    }

    public void setProfiles(final Map<String, String> profiles) {
        this.profiles = profiles;
    }
}

