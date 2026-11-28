package com.moon.catchup.domain.template;

import java.util.List;

import lombok.Getter;

/** 템플릿의 페이지를 저장 순서대로 담는다. */
@Getter
public final class GridTemplateStructureVo {
    private final List<GridTemplatePageVo> pages;

    /**
     * 페이지 목록을 저장 순서대로 복사한다.
     *
     * @param pageList 저장할 순서대로 나열한 페이지
     */
    private GridTemplateStructureVo(List<GridTemplatePageVo> pageList) {
        this.pages = List.copyOf(pageList);
    }

    /**
     * 페이지 순서를 보존하는 템플릿 구조를 만든다.
     *
     * @param pageList 저장할 순서대로 나열한 페이지
     * @return 템플릿 구조
     */
    public static GridTemplateStructureVo of(List<GridTemplatePageVo> pageList) {
        return new GridTemplateStructureVo(pageList);
    }
}
