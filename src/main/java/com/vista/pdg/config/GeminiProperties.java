package com.vista.pdg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(Api api) {
    public record Api(String key) {}
}
