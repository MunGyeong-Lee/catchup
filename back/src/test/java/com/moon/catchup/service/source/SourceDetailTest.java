package com.moon.catchup.service.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.domain.source.SourceAttachmentSummaryVo;
import com.moon.catchup.domain.source.SourceDetailsVo;
import com.moon.catchup.domain.source.SourceEditVo;
import com.moon.catchup.domain.source.SourceEntity;
import com.moon.catchup.domain.source.SourceType;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.repository.source.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;

/** 소스 상세 조회가 저장된 입력과 첨부를 조립하고 누락된 소스를 처리하는 동작을 검증한다. */
public class SourceDetailTest {
    private static final long SOURCE_ID = 42L;

    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final SourceAttachmentRepository sourceAttachmentRepository = mock(SourceAttachmentRepository.class);
    private final SourceManagementService sourceManagementService = new SourceManagementServiceImpl(
            mock(SourceServiceImpl.class), this.sourceRepository, this.sourceAttachmentRepository,
            mock(SourceAttachmentRegistrationServiceImpl.class), mock(ApplicationEventPublisher.class));

    /** 신규 유형의 저장값, 공통 메타데이터와 첨부 목록을 상세 조회 결과에 보존한다. */
    @ParameterizedTest
    @CsvSource({
            "TARGET_COMPANY, companyName, 지원 회사",
            "WRITING_STYLE, content, 간결한 문장",
            "OTHER_TEXT, content, 참고 자료 본문"
    })
    public void returnsStoredDetailsAndAttachments(SourceType sourceType, String fieldName, String fieldValue) {
        Map<String, Object> storedDetails = Map.of(fieldName, fieldValue);
        Instant createdAt = Instant.parse("2026-11-20T01:00:00Z");
        Instant updatedAt = Instant.parse("2026-11-21T01:00:00Z");
        SourceEntity source = mock(SourceEntity.class);
        when(source.getId()).thenReturn(SOURCE_ID);
        when(source.getName()).thenReturn("소스 이름");
        when(source.getType()).thenReturn(sourceType);
        when(source.getDetails()).thenReturn(storedDetails);
        when(source.getCreatedAt()).thenReturn(createdAt);
        when(source.getUpdatedAt()).thenReturn(updatedAt);
        SourceAttachmentSummaryVo attachment = mock(SourceAttachmentSummaryVo.class);
        when(this.sourceRepository.find(SOURCE_ID, false)).thenReturn(Optional.of(source));
        when(this.sourceAttachmentRepository.list(SOURCE_ID)).thenReturn(List.of(attachment));

        SourceEditVo result = this.sourceManagementService.get(SOURCE_ID);
        SourceDetailsVo details = result.getDetails();

        assertThat(result.getSourceId()).isEqualTo(SOURCE_ID);
        assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        assertThat(result.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(details.getName()).isEqualTo("소스 이름");
        assertThat(details.getType()).isEqualTo(sourceType);
        assertThat(details.toStoredFields()).isEqualTo(storedDetails);
        assertThat(result.getAttachments()).containsExactly(attachment);
    }

    /** 소스가 없으면 첨부를 조회하기 전에 소스 없음 오류를 반환한다. */
    @Test
    public void rejectsMissingSourceBeforeLookingUpAttachments() {
        when(this.sourceRepository.find(SOURCE_ID, false)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.sourceManagementService.get(SOURCE_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.SOURCE_NOT_FOUND));
        verifyNoInteractions(this.sourceAttachmentRepository);
    }
}
