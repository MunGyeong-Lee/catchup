package com.moon.catchup.domain.template;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** A4 페이지의 12열 그리드에서 블록의 시작 위치와 열 너비를 담는다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TemplateBlockPlacementVo {
    private final int row;
    private final int column;
    private final int columnSpan;

    /**
     * 주어진 행, 열과 너비를 담는 그리드 배치를 만든다.
     *
     * @param row 시작 행. 블록은 한 행을 차지한다
     * @param column 시작 열
     * @param columnSpan 차지하는 열 수
     * @return 그리드 배치
     */
    public static TemplateBlockPlacementVo of(int row, int column, int columnSpan) {
        return new TemplateBlockPlacementVo(row, column, columnSpan);
    }
}
