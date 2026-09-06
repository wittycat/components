package com.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "glm")
public class GlmConfig {

    private String apiKey;
    private String baseUrl;
    private String model;
    private String embeddingModel;
}
