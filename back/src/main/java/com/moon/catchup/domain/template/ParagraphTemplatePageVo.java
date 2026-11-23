package com.moon.catchup.domain.template;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 자기소개서 문항 하나에 해당하는 A4 페이지의 소제목 자리와 문단 자리를 담는다.
 *
 * <p>소제목 자리 다음에 문단 자리를 목록 순서대로 배치한다. 문항 내용, 제목과
 * 문단 본문 및 작성 지침은 문서에서 다룬다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ParagraphTemplatePageVo {
    private final int headingId;
    private final List<Integer> paragraphIds;

    /**
     * 소제목 자리 하나와 순서가 있는 문단 자리로 자기소개서 페이지를 만든다.
     *
     * @param headingId 소제목 자리의 고정 숫자 식별자
     * @param paragraphIdList 표시 순서대로 나열한 문단 자리 식별자
     * @return 자기소개서 템플릿 페이지
     */
    public static ParagraphTemplatePageVo of(int headingId, List<Integer> paragraphIdList) {
        return new ParagraphTemplatePageVo(headingId, List.copyOf(paragraphIdList));
    }
}
