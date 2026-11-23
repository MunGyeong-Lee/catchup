package com.moon.catchup.domain.template;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 템플릿 블록의 고정 식별자, 유형, 제목과 배치를 담는다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TemplateBlockVo {
    private final int id;
    private final TemplateBlockType type;
    private final String title;
    private final TemplateBlockPlacementVo placement;

    /**
     * 식별자와 유형, 그리드 배치가 지정된 블록을 만든다.
     *
     * @param id 템플릿 내에서 유지하는 양의 정수 식별자
     * @param type 블록 유형
     * @param title 선택 제목
     * @param placement 그리드 배치
     * @return 템플릿 블록
     */
    public static TemplateBlockVo of(int id, TemplateBlockType type, String title,
                                     TemplateBlockPlacementVo placement) {
        return new TemplateBlockVo(id, type, title, placement);
    }
}
