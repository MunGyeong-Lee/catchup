package com.moon.catchup.domain.template;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 템플릿의 페이지를 저장 순서대로 담는다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TemplateStructureVo {
    private final List<TemplatePageVo> pages;

    /**
     * 페이지 순서를 보존하는 템플릿 구조를 만든다.
     *
     * @param pageList 저장할 순서대로 나열한 페이지
     * @return 템플릿 구조
     */
    public static TemplateStructureVo of(List<TemplatePageVo> pageList) {
        return new TemplateStructureVo(List.copyOf(pageList));
    }
}
