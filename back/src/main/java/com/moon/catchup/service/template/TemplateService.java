package com.moon.catchup.service.template;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.controller.template.dto.TemplateCreateRequest;
import com.moon.catchup.domain.template.GridTemplateStructureVo;
import com.moon.catchup.domain.template.ParagraphTemplateStructureVo;
import com.moon.catchup.domain.template.TemplateDocumentType;

/** 문서 유형에 맞는 편집 구조를 검증하고 템플릿을 등록한다. */
public interface TemplateService {

    /**
     * 요청의 편집 구조를 읽고 그리드형 템플릿을 등록한다.
     *
     * <p>페이지와 블록을 VO로 변환한 뒤 그리드 등록 규칙을 검증하고 저장한다.
     *
     * @param documentType 그리드형 문서 유형
     * @param req 제목과 그리드형 편집 구조를 담은 등록 요청
     * @return 등록한 템플릿 식별자
     * @throws ApiException 요청 구조의 형식이나 입력이 유효하지 않은 경우
     */
    public long createGrid(TemplateDocumentType documentType, TemplateCreateRequest req);

    /**
     * 요청의 편집 구조를 읽고 문단형 템플릿을 등록한다.
     *
     * <p>문항별 페이지를 VO로 변환한 뒤 문단형 등록 규칙을 검증하고 저장한다.
     *
     * @param documentType 문단형 문서 유형
     * @param req 제목과 문단형 편집 구조를 담은 등록 요청
     * @return 등록한 템플릿 식별자
     * @throws ApiException 요청 구조의 형식이나 입력이 유효하지 않은 경우
     */
    public long createParagraph(TemplateDocumentType documentType, TemplateCreateRequest req);

    /**
     * 그리드형 템플릿을 등록한다.
     *
     * <p>문서 유형과 제목을 확인한 다음 템플릿 전체의 블록 식별자와
     * 페이지별 12열 배치 범위 및 겹침을 검증한다. 검증한 구조는 JSONB로 저장한다.
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
     * <p>문서 유형과 제목을 확인한 다음 소제목 식별자와 문항별 문단 번호를 검증한다.
     * 문항마다 문단이 한 개 이상 필요하며 번호는 1부터 연속해야 한다. 검증한 구조는 JSONB로 저장한다.
     *
     * @param documentType 문단형 문서 유형
     * @param title 템플릿 제목
     * @param structure 문항별 페이지와 문단 자리 구조
     * @return 등록한 템플릿 식별자
     * @throws ApiException 입력이 등록 규칙을 위반한 경우
     */
    public long createParagraph(TemplateDocumentType documentType, String title, ParagraphTemplateStructureVo structure);
}
