package com.moon.catchup.domain.template;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 자기소개서의 문항별 페이지를 표시 순서대로 담는다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ParagraphTemplateStructureVo {
    private final List<ParagraphTemplatePageVo> pages;

    /**
     * 문항에 대응하는 페이지 목록을 순서대로 복사한다.
     *
     * @param pageList 문항 순서로 나열한 페이지
     * @return 자기소개서 템플릿 구조
     */
    public static ParagraphTemplateStructureVo of(List<ParagraphTemplatePageVo> pageList) {
        return new ParagraphTemplateStructureVo(List.copyOf(pageList));
    }
}
