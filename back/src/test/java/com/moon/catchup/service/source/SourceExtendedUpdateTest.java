package com.moon.catchup.service.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.domain.source.SourceDetailsVo;
import com.moon.catchup.domain.source.SourceEntity;
import com.moon.catchup.domain.source.SourceType;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.repository.source.SourceRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/** 신규 유형의 수정 검증과 저장 호출 순서를 확인한다. */
public class SourceExtendedUpdateTest {
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final SourceAttachmentRepository sourceAttachmentRepository = mock(SourceAttachmentRepository.class);
    private final ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
    private final SourceManagementServiceImpl sourceManagementService = new SourceManagementServiceImpl(
            new SourceServiceImpl(this.sourceRepository, this.sourceAttachmentRepository, this.applicationEventPublisher),
            this.sourceRepository, this.sourceAttachmentRepository,
            mock(SourceAttachmentRegistrationServiceImpl.class), this.applicationEventPublisher);

    /** 요청 유형의 정규화된 상세를 잠근 소스에 저장하고 같은 식별자를 반환한다. */
    @ParameterizedTest
    @CsvSource({"TARGET_COMPANY,companyName", "WRITING_STYLE,content", "OTHER_TEXT,content"})
    public void savesValidatedTargetType(SourceType sourceType, String requiredField) {
        long sourceId = 41L;
        when(this.sourceRepository.find(sourceId, true)).thenReturn(Optional.of(mock(SourceEntity.class)));
        when(this.sourceRepository.update(eq(sourceId), any())).thenReturn(true);

        assertThat(this.sourceManagementService.update(sourceId, sourceType.name(), " New name ",
                Map.of(requiredField, " Saved value "), List.of())).isEqualTo(sourceId);

        ArgumentCaptor<SourceDetailsVo> details = ArgumentCaptor.forClass(SourceDetailsVo.class);
        InOrder writes = inOrder(this.sourceRepository);
        writes.verify(this.sourceRepository).find(sourceId, true);
        writes.verify(this.sourceRepository).update(eq(sourceId), details.capture());
        writes.verify(this.sourceRepository).updateTimestamp(sourceId);
        assertThat(details.getValue().getType()).isEqualTo(sourceType);
        assertThat(details.getValue().getName()).isEqualTo("New name");
        assertThat(details.getValue().toStoredFields()).isEqualTo(Map.of(requiredField, "Saved value"));
        verifyNoInteractions(this.sourceAttachmentRepository, this.applicationEventPublisher);
    }

    /** 필수값을 위반한 수정은 소스를 잠그거나 첨부를 제거하기 전에 실패한다. */
    @ParameterizedTest
    @CsvSource({"TARGET_COMPANY,companyName", "WRITING_STYLE,content", "OTHER_TEXT,content"})
    public void rejectsInvalidTargetBeforeAnyWrite(SourceType sourceType, String requiredField) {
        assertThatThrownBy(() -> this.sourceManagementService.update(41L, sourceType.name(), "Name",
                Map.of(requiredField, " "), List.of(UUID.randomUUID())))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getFieldErrors()).containsOnlyKeys(requiredField);
                });

        verifyNoInteractions(this.sourceRepository, this.sourceAttachmentRepository, this.applicationEventPublisher);
    }
}
