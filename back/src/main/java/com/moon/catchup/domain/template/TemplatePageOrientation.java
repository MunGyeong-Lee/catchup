package com.moon.catchup.domain.template;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** A4 용지의 방향과 밀리미터 단위 치수를 정의한다. */
@Getter
@RequiredArgsConstructor
public enum TemplatePageOrientation {
    PORTRAIT(210, 297), // 세로
    LANDSCAPE(297, 210); // 가로

    private final int width;
    private final int height;
}
