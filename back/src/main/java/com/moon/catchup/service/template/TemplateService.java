package com.moon.catchup.service.template;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.domain.template.GridTemplateStructureVo;
import com.moon.catchup.domain.template.ParagraphTemplateStructureVo;
import com.moon.catchup.domain.template.TemplateDocumentType;

/** 문서 유형에 맞는 편집 구조를 검증하고 템플릿을 등록한다. */
public interface TemplateService {

    /**
     * 그리드형 템플릿을 등록한다.
     *
     * @param documentType 그리드형 문서 유형
     * @param title 템플릿 제목
     * @param structure 페이지와 블록의 그리드 구조
     * @return 등록한 템플릿 식별자
     * @throws ApiException 입력이 등록 규칙을 위반한 경우
     */
    public long createGrid(TemplateDocumentType documentType, String title, GridTemplateStructureVo structure);

    /**
     * 문단형 템플릿을 등록한다.
     *
     * @param documentType 문단형 문서 유형
     * @param title 템플릿 제목
     * @param structure 문항별 페이지와 문단 자리 구조
     * @return 등록한 템플릿 식별자
     * @throws ApiException 입력이 등록 규칙을 위반한 경우
     */
    public long createParagraph(TemplateDocumentType documentType, String title, ParagraphTemplateStructureVo structure);
}
