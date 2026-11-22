package com.moon.catchup.domain.template;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 등록 입력 또는 조회 결과의 템플릿 값을 담는다.
 *
 * <p>편집 구조는 페이지, 블록과 그리드 배치를 포함한 JSON 객체 문자열로 전달한다.
 * 블록은 템플릿 안에서 고유한 숫자 식별자를 가지며 배치가 바뀌어도 식별자를 유지한다.
 * 지원하는 문서 유형과 편집 구조의 세부 규칙은 등록 서비스에서 검증한다.
 */
@Getter
@AllArgsConstructor
public final class TemplateVo {
    private final Long templateId;
    private final String documentType;
    private final String title;
    private final String structureJson;
    private final Instant createdAt;

    /**
     * 조회한 엔티티에서 템플릿의 저장값과 메타데이터를 초기화한다.
     *
     * @param template DB에서 조회한 템플릿 엔티티
     */
    private TemplateVo(TemplateEntity template) {
        this.templateId = template.getId();
        this.documentType = template.getDocumentType();
        this.title = template.getTitle();
        this.structureJson = template.getStructureJson();
        this.createdAt = template.getCreatedAt();
    }

    /**
     * 조회한 템플릿의 저장값과 메타데이터를 복사한다.
     *
     * @param template 조회한 템플릿
     * @return 저장된 템플릿 값
     */
    public static TemplateVo from(TemplateEntity template) {
        return new TemplateVo(template);
    }

    /**
     * 식별자와 등록 시각을 DB가 생성하는 새 템플릿을 만든다.
     *
     * @return 등록에 사용할 템플릿 엔티티
     */
    public TemplateEntity convertToTemplateEntity() {
        return new TemplateEntity(this);
    }
}
