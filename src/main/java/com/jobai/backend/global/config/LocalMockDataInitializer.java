package com.jobai.backend.global.config;

import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("local") // 로컬 프로필일 때만 이 클래스가 스프링에 등록
@RequiredArgsConstructor
public class LocalMockDataInitializer implements ApplicationRunner {

    private final MemberRepository memberRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String mockEmail = "test@jobai.com";

        // 1. 이미 해당 이메일의 가짜 유저가 있는지 더블 체크
        if (!memberRepository.existsByEmail(mockEmail)) {
            log.info("[Local Auth] 로컬 개발용 가짜 유저(test@jobai.com)가 없어 자동 생성을 시작합니다.");

            // 2. Member 엔티티 규격에 맞게 빌더로 생성 (기존 Member 필드 스펙에 맞추세요)
            Member mockMember = Member.builder()
                    .email(mockEmail)
                    .name("이정헌")
                    .build();

            memberRepository.save(mockMember);
            log.info("[Local Auth] 가짜 유저 생성이 성공적으로 완료되었습니다.");
        } else {
            log.info("[Local Auth] 이미 가짜 유저(test@jobai.com)가 DB에 존재합니다.");
        }
    }
}