package com.example.policy.config;

import com.fasterxml.jackson.core.JsonParser;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonSecurityConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer failOnDuplicateJsonKeys() {
        return builder -> builder.postConfigurer(mapper ->
                mapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    }
}
