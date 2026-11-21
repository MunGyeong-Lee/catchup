package com.moon.catchup.common.http;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

/** 목록 항목과 페이지 정보를 전달한다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageResponse<T> {
    private final List<T> items;
    @Schema(description = "1부터 시작하는 현재 페이지 번호")
    private final int pageNumber;
    private final int pageSize;
    private final int totalPages;
    private final long totalElements;

    /** Spring Data 페이지를 1부터 시작하는 API 페이지 응답으로 변환한다. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber() + 1,
                page.getSize(), page.getTotalPages(), page.getTotalElements());
    }
}
