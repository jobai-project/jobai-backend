package com.jobai.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = BackendApplicationTests.TestApplication.class)
@ActiveProfiles("test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@ComponentScan(
			basePackages = "com.jobai.backend",
			excludeFilters = {
					@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = BackendApplication.class),
					@ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.jobai\\.backend\\..*Test.*")
			}
	)
	static class TestApplication {
	}

}
