package com.jobai.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.lambda.LambdaClient;

@Configuration
@Profile("!collect & !classify & !export")
public class LambdaConfig {

    @Bean
    public LambdaClient lambdaClient(AwsCredentialsProvider credentialsProvider, AwsRegionProvider regionProvider) {
        return LambdaClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(regionProvider.getRegion())
                .build();
    }
}