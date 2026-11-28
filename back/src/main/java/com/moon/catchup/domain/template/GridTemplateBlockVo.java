package com.moon.catchup.domain.template;

import lombok.Getter;

/** 템플릿 블록의 고정 식별자, 유형, 제목과 배치를 담는다. */
@Getter
public final class GridTemplateBlockVo {
    private final int id;
    private final TemplateBlockType type;
    private final String title;
    private final GridTemplateBlockPlacementVo placement;

    /**
     * 식별자, 유형, 선택 제목과 그리드 배치를 초기화한다.
     *
     * @param id 블록 식별자
     * @param type 블록 유형
     * @param title 선택 제목
     * @param placement 그리드 배치
     */
    private GridTemplateBlockVo(int id, TemplateBlockType type, String title, GridTemplateBlockPlacementVo placement) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.placement = placement;
    }

    /**
     * 식별자와 유형, 그리드 배치가 지정된 블록을 만든다.
     *
     * @param id 템플릿 내에서 유지하는 양의 정수 식별자
     * @param type 블록 유형
     * @param title 선택 제목
     * @param placement 그리드 배치
     * @return 템플릿 블록
     */
    public static GridTemplateBlockVo of(int id, TemplateBlockType type, String title,
                                         GridTemplateBlockPlacementVo placement) {
        return new GridTemplateBlockVo(id, type, title, placement);
    }
}
