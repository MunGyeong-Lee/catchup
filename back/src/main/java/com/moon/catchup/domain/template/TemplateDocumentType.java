package com.moon.catchup.domain.template;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 지원하는 문서 유형과 각 유형의 템플릿 배치 방식을 정의한다. */
@Getter
@RequiredArgsConstructor
public enum TemplateDocumentType {
    RESUME(TemplateLayoutType.GRID),
    PORTFOLIO(TemplateLayoutType.GRID),
    CAREER_DESCRIPTION(TemplateLayoutType.GRID),
    COVER_LETTER(TemplateLayoutType.PARAGRAPH);

    private final TemplateLayoutType layoutType;
}
