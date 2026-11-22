package com.moon.catchup.service.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.domain.source.SourceEntity;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.repository.source.SourceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

/** 소스 삭제의 잠금, DB 쓰기, 커밋 후 정리 이벤트 요청 순서를 검증한다. */
public class SourceExtendedDeleteTest {
    private static final long SOURCE_ID = 42L;

    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final SourceAttachmentRepository attachmentRepository = mock(SourceAttachmentRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SourceManagementServiceImpl sourceManagementService = new SourceManagementServiceImpl(
            new SourceServiceImpl(this.sourceRepository, this.attachmentRepository, this.eventPublisher),
            this.sourceRepository, this.attachmentRepository,
            mock(SourceAttachmentRegistrationServiceImpl.class), this.eventPublisher);

    /** 소스 삭제에 성공하면 제거한 첨부 키만 정리 이벤트에 전달한다. */
    @Test
    public void publishesOnlyRemovedKeysAfterDatabaseWrites() {
        when(this.sourceRepository.find(SOURCE_ID, true)).thenReturn(Optional.of(mock(SourceEntity.class)));
        when(this.attachmentRepository.removeAll(SOURCE_ID)).thenReturn(List.of("deleted-key"));

        this.sourceManagementService.delete(SOURCE_ID);

        ArgumentCaptor<SourceAttachmentCleanup.Removed> removed =
                ArgumentCaptor.forClass(SourceAttachmentCleanup.Removed.class);
        InOrder calls = inOrder(this.sourceRepository, this.attachmentRepository, this.eventPublisher);
        calls.verify(this.sourceRepository).find(SOURCE_ID, true);
        calls.verify(this.attachmentRepository).removeAll(SOURCE_ID);
        calls.verify(this.attachmentRepository).detachCompletedAttempts(SOURCE_ID);
        calls.verify(this.sourceRepository).delete(SOURCE_ID);
        calls.verify(this.eventPublisher).publishEvent(removed.capture());
        assertThat(removed.getValue().getKeys()).containsExactly("deleted-key");
    }

    /** 없는 소스는 첨부 삭제와 원본 정리 요청 전에 종료한다. */
    @Test
    public void rejectsMissingSourceBeforeAttachmentChanges() {
        when(this.sourceRepository.find(SOURCE_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.sourceManagementService.delete(SOURCE_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.SOURCE_NOT_FOUND));
        verifyNoInteractions(this.attachmentRepository, this.eventPublisher);
    }

    /** 소스 DB 삭제가 실패하면 첨부 원본 정리를 요청하지 않는다. */
    @Test
    public void leavesOriginalCleanupUnscheduledWhenDeleteFails() {
        when(this.sourceRepository.find(SOURCE_ID, true)).thenReturn(Optional.of(mock(SourceEntity.class)));
        when(this.attachmentRepository.removeAll(SOURCE_ID)).thenReturn(List.of("deleted-key"));
        doThrow(new DataIntegrityViolationException("Injected failure")).when(this.sourceRepository).delete(SOURCE_ID);

        assertThatThrownBy(() -> this.sourceManagementService.delete(SOURCE_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(this.eventPublisher);
    }
}
