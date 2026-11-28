package com.moon.catchup.domain.template;

import java.time.Instant;

import lombok.Getter;

/**
 * 등록 입력 또는 조회 결과의 템플릿 값을 담는다.
 *
 * <p>편집 구조는 문서 유형에 대응하는 JSON 객체 문자열로 전달한다.
 * 그리드 구조는 페이지와 블록 배치를, 문단 구조는 문항 페이지와 소제목 및 문단 자리를 담는다.
 * 지원하는 문서 유형과 편집 구조의 세부 규칙은 등록 서비스에서 검증한다.
 */
@Getter
public final class TemplateVo {
    private final Long templateId;
    private final TemplateDocumentType documentType;
    private final String title;
    private final String structureJson;
    private final Instant createdAt;

    /**
     * DB에서 식별자와 등록 시각을 받을 템플릿의 입력값을 초기화한다.
     *
     * @param documentType 문서 유형
     * @param title 템플릿 제목
     * @param structureJson 편집 구조 JSON
     */
    private TemplateVo(TemplateDocumentType documentType, String title, String structureJson) {
        this.templateId = null;
        this.documentType = documentType;
        this.title = title;
        this.structureJson = structureJson;
        this.createdAt = null;
    }

    /**
     * 검증된 입력으로 등록용 템플릿 값을 만든다.
     *
     * @param documentType 문서 유형
     * @param title 템플릿 제목
     * @param structureJson 편집 구조 JSON
     * @return 등록용 템플릿 값
     */
    public static TemplateVo of(TemplateDocumentType documentType, String title, String structureJson) {
        return new TemplateVo(documentType, title, structureJson);
    }

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
