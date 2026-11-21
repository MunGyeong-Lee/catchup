package com.moon.catchup.service.source;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.storage.source.SourceAttachmentStorage;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** 일일 원본 대조가 실패 및 보호 대상을 처리하는 방식의 단위 테스트다. */
public class SourceAttachmentCleanupTest {
    private final SourceAttachmentRepository repository = mock(SourceAttachmentRepository.class);
    private final SourceAttachmentStorage storage = mock(SourceAttachmentStorage.class);
    private final SourceAttachmentCleanup cleanup =
            new SourceAttachmentCleanup(repository, storage, mock(ExecutorService.class));

    /** 데이터베이스 참조 조회가 실패하면 후보를 하나도 삭제하지 않는다. */
    @Test
    public void skipsDeletionWhenDatabaseLookupFails() {
        when(storage.listKeys()).thenReturn(List.of("orphan"));
        when(repository.protectedKeys()).thenThrow(new DataAccessResourceFailureException("Injected failure"));
        cleanup.reconcile();
        verify(storage).listKeys();
        verifyNoMoreInteractions(storage);
    }

    /** 저장소 목록을 완성하지 못한 경우 데이터베이스 대조와 삭제를 건너뛴다. */
    @Test
    public void skipsDeletionWhenListingFails() {
        when(storage.listKeys()).thenThrow(new IllegalStateException("Injected pagination failure"));
        cleanup.reconcile();
        verify(storage).listKeys();
        verifyNoMoreInteractions(storage);
        verifyNoInteractions(repository);
    }

    /** 등록된 첨부와 업로드 예약 키를 보존하며 반복 실행할 수 있다. */
    @Test
    public void removesOnlyOrphanedKeys() {
        when(storage.listKeys()).thenReturn(List.of("registered", "reserved", "orphan"))
                .thenReturn(List.of("registered", "reserved"));
        when(repository.protectedKeys()).thenReturn(Set.of("registered", "reserved"));
        cleanup.reconcile();
        cleanup.reconcile();
        verify(storage, times(2)).listKeys();
        verify(storage).delete("orphan");
        verifyNoMoreInteractions(storage);
    }
}
