package com.jobai.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@Profile("!collect & !classify & !export")
public class SesConfig {

    @Bean
    public SesV2Client sesV2Client(AwsCredentialsProvider credentialsProvider, AwsRegionProvider regionProvider) {
        return SesV2Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(regionProvider.getRegion())
                .build();
    }
}
