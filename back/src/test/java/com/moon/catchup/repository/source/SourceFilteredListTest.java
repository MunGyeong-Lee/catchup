package com.moon.catchup.repository.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.moon.catchup.domain.source.SourceSummaryVo;
import com.moon.catchup.domain.source.SourceType;
import com.moon.catchup.service.source.SourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL에 저장한 소스의 유형 필터, 정렬과 페이지 집계를 검증한다. */
@SpringBootTest
@Transactional
public class SourceFilteredListTest {
    @Autowired private SourceService sourceService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long earlierCompanyId;
    private long firstTiedCompanyId;
    private long secondTiedCompanyId;
    private long writingStyleId;
    private long originalSourceCount;

    /** 여러 유형과 등록 시각 동률을 갖는 소스를 준비한다. */
    @BeforeEach
    public void prepareSources() {
        this.originalSourceCount = this.jdbcTemplate.queryForObject("SELECT count(*) FROM sources", Long.class);
        this.earlierCompanyId = insertSource(SourceType.TARGET_COMPANY, "Alpha", "2999-01-01");
        this.firstTiedCompanyId = insertSource(SourceType.TARGET_COMPANY, "Zulu", "2999-02-01");
        this.secondTiedCompanyId = insertSource(SourceType.TARGET_COMPANY, "Alpha", "2999-02-01");
        this.writingStyleId = insertSource(SourceType.WRITING_STYLE, "Style", "2999-03-01");
        this.jdbcTemplate.update("""
                INSERT INTO attachments(attachment_id, source_id, original_name, content_type, size_bytes, object_key)
                VALUES (gen_random_uuid(), ?, 'source.pdf', 'application/pdf', 10, gen_random_uuid()::text)
                """, this.firstTiedCompanyId);
    }

    /** 유형을 골라낸 뒤 기본 정렬과 페이지 범위를 적용하고 공통 이름과 첨부 유무를 제공한다. */
    @Test
    public void filtersBeforePagingAndCountsOnlyMatches() {
        Page<SourceSummaryVo> firstPage = this.sourceService.list(1, 2, null, SourceType.TARGET_COMPANY);
        assertEquals(List.of(this.secondTiedCompanyId, this.firstTiedCompanyId), sourceIds(firstPage));
        assertEquals(List.of("Alpha", "Zulu"), firstPage.getContent().stream().map(SourceSummaryVo::getName).toList());
        assertTrue(firstPage.getContent().stream().allMatch(source -> source.getType() == SourceType.TARGET_COMPANY));
        assertFalse(firstPage.getContent().getFirst().isHasAttachments());
        assertTrue(firstPage.getContent().getLast().isHasAttachments());
        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(List.of(this.earlierCompanyId), sourceIds(
                this.sourceService.list(2, 2, null, SourceType.TARGET_COMPANY)));

        Page<SourceSummaryVo> stylePage = this.sourceService.list(1, 2, null, SourceType.WRITING_STYLE);
        assertEquals(List.of(this.writingStyleId), sourceIds(stylePage));
        assertEquals(1, stylePage.getTotalElements());

        Page<SourceSummaryVo> emptyPage = this.sourceService.list(1, 2, null, SourceType.OTHER_TEXT);
        assertTrue(emptyPage.isEmpty());
        assertEquals(0, emptyPage.getTotalElements());
        assertEquals(0, emptyPage.getTotalPages());
    }

    /** 선택 정렬을 먼저 적용하고 동률은 기존 기본 순서로 정렬한다. */
    @Test
    public void retainsStableTiesWithRequestedSort() {
        Page<SourceSummaryVo> page = this.sourceService.list(1, 2, "name:ASC", SourceType.TARGET_COMPANY);
        assertEquals(List.of(this.secondTiedCompanyId, this.earlierCompanyId), sourceIds(page));

        Page<SourceSummaryVo> equalNamesAndTime = this.sourceService.list(
                1, 2, "createdAt:DESC", SourceType.TARGET_COMPANY);
        assertEquals(List.of(this.secondTiedCompanyId, this.firstTiedCompanyId), sourceIds(equalNamesAndTime));
    }

    /** 범위를 벗어난 일반·초대형 페이지에도 유형별 전체 건수와 총 페이지 수를 유지한다. */
    @Test
    public void retainsFilteredTotalsBeyondLastPageAndJpaOffsetLimit() {
        for (int pageNumber : List.of(3, Integer.MAX_VALUE)) {
            Page<SourceSummaryVo> page = this.sourceService.list(pageNumber, 2, null, SourceType.TARGET_COMPANY);
            assertTrue(page.isEmpty());
            assertEquals(pageNumber - 1, page.getNumber());
            assertEquals(3, page.getTotalElements());
            assertEquals(2, page.getTotalPages());
        }
    }

    /** 타입 생략과 기존 진입점에 동일한 무필터 조회를 적용한다. */
    @Test
    public void keepsUnfilteredEntryPoint() {
        Page<SourceSummaryVo> original = this.sourceService.list(1, 4, null);
        Page<SourceSummaryVo> unfiltered = this.sourceService.list(1, 4, null, null);
        assertEquals(List.of(this.writingStyleId, this.secondTiedCompanyId,
                this.firstTiedCompanyId, this.earlierCompanyId), sourceIds(original));
        assertEquals(original, unfiltered);
        assertEquals(this.originalSourceCount + 4, unfiltered.getTotalElements());
    }

    /** 등록 시각을 제어할 수 있는 검증 행을 저장하고 식별자를 읽는다. */
    private long insertSource(SourceType sourceType, String name, String createdDate) {
        return this.jdbcTemplate.queryForObject("""
                INSERT INTO sources(source_type, name, details, created_at)
                VALUES (?, ?, '{}', CAST(? AS timestamptz)) RETURNING source_id
                """, Long.class, sourceType.name(), name, createdDate + "T00:00:00Z");
    }

    /** 페이지 항목의 소스 식별자를 조회 순서대로 반환한다. */
    private static List<Long> sourceIds(Page<SourceSummaryVo> page) {
        return page.getContent().stream().map(SourceSummaryVo::getId).toList();
    }
}
