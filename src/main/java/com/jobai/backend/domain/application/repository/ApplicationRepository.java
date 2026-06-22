package com.jobai.backend.domain.application.repository;

import com.jobai.backend.domain.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
}
