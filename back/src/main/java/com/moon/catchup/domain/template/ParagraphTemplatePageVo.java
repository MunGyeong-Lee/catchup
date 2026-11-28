package com.moon.catchup.domain.template;

import java.util.List;

import lombok.Getter;

/**
 * 자기소개서 문항 하나에 해당하는 A4 페이지의 소제목 자리와 문단 자리를 담는다.
 *
 * <p>소제목 자리 다음에 문단 자리를 목록 순서대로 배치한다. 문항 내용, 제목과
 * 문단 본문 및 작성 지침은 문서에서 다룬다.
 */
@Getter
public final class ParagraphTemplatePageVo {
    private final int headingId;
    private final List<Integer> paragraphIds;

    /**
     * 소제목 식별자를 초기화하고 문단 식별자 목록을 표시 순서대로 복사한다.
     *
     * @param headingId 소제목 자리의 식별자
     * @param paragraphIdList 표시 순서대로 나열한 문단 자리 식별자
     */
    private ParagraphTemplatePageVo(int headingId, List<Integer> paragraphIdList) {
        this.headingId = headingId;
        this.paragraphIds = List.copyOf(paragraphIdList);
    }

    /**
     * 소제목 자리 하나와 순서가 있는 문단 자리로 자기소개서 페이지를 만든다.
     *
     * @param headingId 소제목 자리의 고정 숫자 식별자
     * @param paragraphIdList 표시 순서대로 나열한 문단 자리 식별자
     * @return 자기소개서 템플릿 페이지
     */
    public static ParagraphTemplatePageVo of(int headingId, List<Integer> paragraphIdList) {
        return new ParagraphTemplatePageVo(headingId, paragraphIdList);
    }
}
