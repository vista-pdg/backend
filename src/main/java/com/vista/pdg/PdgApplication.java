package com.vista.pdg;

import com.vista.pdg.config.GeminiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GeminiProperties.class)
public class PdgApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdgApplication.class, args);
    }
}
