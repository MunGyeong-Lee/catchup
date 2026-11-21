package com.moon.catchup.service.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.moon.catchup.domain.source.SourceDetailsVo;
import com.moon.catchup.domain.source.SourceType;
import com.moon.catchup.domain.source.UploadAttemptVo;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.storage.source.SourceAttachmentStorage;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 첨부 등록 서비스가 업로드와 데이터베이스 완료 실패 상황을 처리하는지 검증한다. */
public class SourceAttachmentRegistrationTest {
    /** 저장 실패를 알리고 업로드 대상으로 기록한 객체를 정리하는지 확인한다. */
    @Test
    public void storageFailureIsReportedAndCompensated() throws Exception {
        SourceAttachmentStorage storage = mock(SourceAttachmentStorage.class);
        doThrow(new IOException("storage unavailable")).when(storage).put(anyString(), anyString(), anyLong(), any());
        SourceAttachmentRepository repository = mock(SourceAttachmentRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        SourceServiceImpl sources = mock(SourceServiceImpl.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation ->
                new SimpleTransactionStatus());
        when(sources.validate(anyString(), anyString(), anyMap())).thenReturn(
                SourceDetailsVo.fromValidated(SourceType.EXPERIENCE, "소스 제목",
                        Map.of("title", "example", "contribution", "example")));
        when(repository.lockAttempt(any(UUID.class), eq(false)))
                .thenReturn(Optional.of(new UploadAttemptVo(List.of("test-key"), null)));
        SourceAttachmentRegistrationServiceImpl service = new SourceAttachmentRegistrationServiceImpl(repository,
                transactionalOperations(repository, sources, storage, transactions), sources,
                new SourceAttachmentValidator());
        SourceAttachmentValidator.File file = new SourceAttachmentValidator.File(SourceAttachmentValidationTest.pdf(), "sample.pdf", "application/pdf");
        assertThrows(IllegalStateException.class,
                () -> service.register(UUID.randomUUID(), "EXPERIENCE", "소스 제목", Map.of("title", "example"), List.of(file)));
        verify(storage).delete(anyString());
    }

    /** 커밋 응답이 유실되어도 완료된 저장의 객체를 삭제하지 않는지 확인한다. */
    @Test
    public void uncertainCommitReturnsConfirmedSourceWithoutDeletingObjects() throws Exception {
        SourceAttachmentStorage storage = mock(SourceAttachmentStorage.class);
        SourceAttachmentRepository repository = mock(SourceAttachmentRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        SourceServiceImpl sources = mock(SourceServiceImpl.class);
        long completed = 42L;
        when(transactions.getTransaction(any())).thenAnswer(invocation ->
                new SimpleTransactionStatus());
        doNothing().doThrow(new TransactionSystemException("Commit response lost"))
                .doNothing().when(transactions).commit(any());
        when(sources.validate(anyString(), anyString(), anyMap())).thenReturn(
                SourceDetailsVo.fromValidated(SourceType.EXPERIENCE, "소스 제목",
                        Map.of("title", "example", "contribution", "example")));
        when(sources.insert(any(SourceDetailsVo.class))).thenReturn(completed);
        when(repository.lockAttempt(any(UUID.class), eq(false)))
                .thenReturn(Optional.of(new UploadAttemptVo(List.of("test-key"), null)))
                .thenReturn(Optional.of(new UploadAttemptVo(List.of("test-key"), completed)));
        SourceAttachmentRegistrationServiceImpl service = new SourceAttachmentRegistrationServiceImpl(repository,
                transactionalOperations(repository, sources, storage, transactions), sources,
                new SourceAttachmentValidator());
        SourceAttachmentValidator.File file = new SourceAttachmentValidator.File(SourceAttachmentValidationTest.pdf(), "sample.pdf", "application/pdf");
        assertEquals(completed, service.register(UUID.randomUUID(), "EXPERIENCE", "소스 제목", Map.of(), List.of(file)));
        verify(storage, never()).delete(anyString());
    }

    /** Spring 프록시를 통해 운영 코드와 같은 선언적 트랜잭션을 적용한다. */
    private static SourceAttachmentTransactionService transactionalOperations(
            SourceAttachmentRepository repository, SourceServiceImpl sources,
            SourceAttachmentStorage storage, PlatformTransactionManager manager) {
        ProxyFactory proxy = new ProxyFactory(new SourceAttachmentTransactionService(repository, sources, storage));
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        proxy.addAdvice(interceptor);
        return (SourceAttachmentTransactionService) proxy.getProxy();
    }
}
