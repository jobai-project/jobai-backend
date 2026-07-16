package com.jobai.backend.domain.member.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemberRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void onboardingCareerTypesAcceptOnlySupportedValues() {
        MemberRequestDTO.UpdateBasicInfoDTO valid = MemberRequestDTO.UpdateBasicInfoDTO.builder()
                .careerType(List.of("인턴", "신입", "경력직", "계약직"))
                .locations(List.of("서울"))
                .build();
        MemberRequestDTO.UpdateBasicInfoDTO invalid = MemberRequestDTO.UpdateBasicInfoDTO.builder()
                .careerType(List.of("정규직"))
                .locations(List.of("서울"))
                .build();

        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(invalid)).isNotEmpty();
    }

    @Test
    void onboardingCareerTypesMustNotBeEmpty() {
        MemberRequestDTO.UpdateBasicInfoDTO request = MemberRequestDTO.UpdateBasicInfoDTO.builder()
                .careerType(List.of())
                .locations(List.of("서울"))
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("careerType");
    }

    @Test
    void jobPreferenceCareerTypesUseSameAllowedValues() {
        MemberRequestDTO.UpdateJobPreferenceDTO request = MemberRequestDTO.UpdateJobPreferenceDTO.builder()
                .careerType(List.of("developer"))
                .build();

        assertThat(validator.validate(request)).isNotEmpty();
    }
}