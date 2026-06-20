package com.jobai.backend.domain.crawler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 크롤러용 YAML 파서 빈.
 *
 * <p>기본 ObjectMapper(JSON)와 충돌하지 않도록 별도 이름(yamlMapper)으로 등록한다.
 * 스펙 YAML(specs/*.yaml)을 CrawlSpec 으로 읽을 때 쓴다.
 */
@Configuration
public class CrawlerConfig {

    @Bean
    public ObjectMapper yamlMapper() {
        return new ObjectMapper(new YAMLFactory());
    }
}