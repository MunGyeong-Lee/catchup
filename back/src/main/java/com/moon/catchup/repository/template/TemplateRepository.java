package com.moon.catchup.repository.template;

import com.moon.catchup.domain.template.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 템플릿의 저장과 식별자 조회를 처리한다. */
public interface TemplateRepository extends JpaRepository<TemplateEntity, Long> {
}
