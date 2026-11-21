package com.moon.catchup.common.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** 공통 페이지 응답이 항목과 페이지 정보를 전달하는지 검증한다. */
public class PageResponseTest {
    /** 항목 타입을 유지하고 API 페이지 번호를 1부터 반환한다. */
    @Test
    public void returnsItemsAndOneBasedPageNumber() {
        PageImpl<String> page = new PageImpl<>(List.of("first", "second"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertEquals(List.of("first", "second"), response.getItems());
        assertEquals(2, response.getPageNumber());
        assertEquals(2, response.getPageSize());
        assertEquals(3, response.getTotalPages());
        assertEquals(5, response.getTotalElements());
    }
}
