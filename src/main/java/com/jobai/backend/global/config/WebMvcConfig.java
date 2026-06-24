package com.jobai.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class WebMvcConfig {

    /**
     * CrawlerConfig의 yamlMapper가 ObjectMapper 타입으로 등록되면
     * JacksonAutoConfiguration(@ConditionalOnMissingBean)이 기본 JSON ObjectMapper 생성을 건너뜀.
     * 명시적으로 @Primary JSON ObjectMapper를 등록해 Spring MVC가 JSON을 사용하게 함.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
