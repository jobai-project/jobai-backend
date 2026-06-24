package com.jobai.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Disabled("CI 환경에 외부 인프라(DB, Redis)가 없어 컨텍스트 로드 실패")
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
