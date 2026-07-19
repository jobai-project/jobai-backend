package com.jobai.backend.domain.home.repository;

import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository.ScoredJobCandidate;
import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.global.enums.JobCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(HomeJobCandidateRepository.class)
@Testcontainers(disabledWithoutDocker = true)
class HomeJobCandidateRepositoryIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("jobai_repository_test")
            .withUsername("jobai")
            .withPassword("jobai")
            .withInitScript("init-pgvector.sql");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private HomeJobCandidateRepository repository;

    @Test
    @DisplayName("최근 1000건 밖의 고득점 공고를 실제 점수 JOIN으로 조회하고 비대상 카테고리는 제외한다")
    void findsOlderHighScoreBeyondOneThousandCandidates() {
        Member member = Member.builder().email("repository-integration@jobai.com").build();
        entityManager.persist(member);
        Resumes resume = Resumes.builder()
                .member(member)
                .originalFilename("resume.pdf")
                .isActive(true)
                .updatedAt(LocalDate.of(2026, 7, 18))
                .build();
        entityManager.persist(resume);

        LocalDateTime baseTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        Long expectedPostingId = null;
        for (int i = 0; i <= 1_000; i++) {
            PrivateJobPosting posting = posting(
                    "valid-" + i,
                    JobCategory.BACKEND.getLabel(),
                    baseTime.plusSeconds(i)
            );
            entityManager.persist(posting);
            int score = i == 0 ? 99 : 70;
            entityManager.persist(score(member, resume, posting, score));
            if (i == 0) {
                expectedPostingId = posting.getId();
            }
        }

        PrivateJobPosting excluded = posting("excluded", JobCategory.NON_TARGET.getLabel(), baseTime.plusDays(1));
        entityManager.persist(excluded);
        entityManager.persist(score(member, resume, excluded, 100));
        entityManager.flush();
        entityManager.clear();

        List<ScoredJobCandidate> result = repository.findScoredPrivateCandidates(
                resume.getId(), null, null, 70, 1
        );
        long totalCount = repository.countScoredPrivateCandidates(resume.getId(), null, null, 70);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).candidate().id()).isEqualTo(expectedPostingId);
        assertThat(result.get(0).score()).isEqualTo(99);
        assertThat(totalCount).isEqualTo(1_001);
    }

    private PrivateJobPosting posting(String sourceJobId, String category, LocalDateTime createdAt) {
        return PrivateJobPosting.builder()
                .company("test-company")
                .sourceJobId(sourceJobId)
                .title("Backend Engineer " + sourceJobId)
                .location("서울")
                .employmentType("신입")
                .jobCategory(category)
                .isClosed(false)
                .lastSeenAt(createdAt)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    private PrivateMatchScore score(
            Member member, Resumes resume, PrivateJobPosting posting, int value
    ) {
        return PrivateMatchScore.builder()
                .member(member)
                .resume(resume)
                .privateJobPosting(posting)
                .score(value)
                .build();
    }
}
