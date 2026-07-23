package com.jobai.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * collect / classify / export 프로필에서 AWS 관련 빈이 없어 기동이 실패하는 문제를 막는 설정.
 * SesConfig 가 이 프로필에서 SesV2Client 를 만들지 않으므로 더미 빈을 제공한다.
 * 실제 이메일 발송은 이 모드에서 일어나지 않는다.
 */
@Configuration
@Profile("collect | classify | export")
public class CollectModeConfig {

    @Bean
    public SesV2Client sesV2Client() {
        return SesV2Client.builder()
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .region(Region.AP_NORTHEAST_2)
                .build();
    }
}
