package com.moon.catchup.careeritem.service;

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

import com.moon.catchup.careeritem.domain.CareerItemDetailsVo;
import com.moon.catchup.careeritem.domain.CareerItemType;
import com.moon.catchup.careeritem.repository.CareerItemAttachmentRepository;
import com.moon.catchup.careeritem.storage.CareerItemAttachmentStorage;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 첨부 등록 서비스가 업로드와 데이터베이스 완료 실패 상황을 처리하는지 검증한다. */
class CareerItemAttachmentRegistrationTest {
    /** 저장 실패를 알리고 업로드 대상으로 기록한 객체를 정리하는지 확인한다. */
    @Test
    void storageFailureIsReportedAndCompensated() throws Exception {
        CareerItemAttachmentStorage storage = mock(CareerItemAttachmentStorage.class);
        doThrow(new IOException("storage unavailable")).when(storage).put(anyString(), anyString(), anyLong(), any());
        CareerItemAttachmentRepository repository = mock(CareerItemAttachmentRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        CareerItemServiceImpl careerItems = mock(CareerItemServiceImpl.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation ->
                new SimpleTransactionStatus());
        when(careerItems.validate(anyString(), anyMap())).thenReturn(
                CareerItemDetailsVo.fromValidated(CareerItemType.EXPERIENCE,
                        Map.of("title", "example", "contribution", "example")));
        when(repository.lockAttempt(any(UUID.class), eq(false)))
                .thenReturn(Optional.of(new CareerItemAttachmentRepository.UploadAttempt(List.of("test-key"), null)));
        CareerItemAttachmentRegistrationServiceImpl service = new CareerItemAttachmentRegistrationServiceImpl(repository,
                transactionalOperations(repository, careerItems, storage, transactions), careerItems,
                new CareerItemAttachmentValidator());
        CareerItemAttachmentValidator.File file = new CareerItemAttachmentValidator.File(CareerItemAttachmentValidationTest.pdf(), "sample.pdf", "application/pdf");
        assertThrows(IllegalStateException.class,
                () -> service.register(UUID.randomUUID(), "EXPERIENCE", Map.of("title", "example"), List.of(file)));
        verify(storage).delete(anyString());
    }

    /** 커밋 응답이 유실되어도 완료된 저장의 객체를 삭제하지 않는지 확인한다. */
    @Test
    void uncertainCommitReturnsConfirmedCareerItemWithoutDeletingObjects() throws Exception {
        CareerItemAttachmentStorage storage = mock(CareerItemAttachmentStorage.class);
        CareerItemAttachmentRepository repository = mock(CareerItemAttachmentRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        CareerItemServiceImpl careerItems = mock(CareerItemServiceImpl.class);
        long completed = 42L;
        when(transactions.getTransaction(any())).thenAnswer(invocation ->
                new SimpleTransactionStatus());
        doNothing().doThrow(new TransactionSystemException("Commit response lost"))
                .doNothing().when(transactions).commit(any());
        when(careerItems.validate(anyString(), anyMap())).thenReturn(
                CareerItemDetailsVo.fromValidated(CareerItemType.EXPERIENCE,
                        Map.of("title", "example", "contribution", "example")));
        when(careerItems.insert(any(CareerItemDetailsVo.class))).thenReturn(completed);
        when(repository.lockAttempt(any(UUID.class), eq(false)))
                .thenReturn(Optional.of(new CareerItemAttachmentRepository.UploadAttempt(List.of("test-key"), null)))
                .thenReturn(Optional.of(new CareerItemAttachmentRepository.UploadAttempt(List.of("test-key"), completed)));
        CareerItemAttachmentRegistrationServiceImpl service = new CareerItemAttachmentRegistrationServiceImpl(repository,
                transactionalOperations(repository, careerItems, storage, transactions), careerItems,
                new CareerItemAttachmentValidator());
        CareerItemAttachmentValidator.File file = new CareerItemAttachmentValidator.File(CareerItemAttachmentValidationTest.pdf(), "sample.pdf", "application/pdf");
        assertEquals(completed, service.register(UUID.randomUUID(), "EXPERIENCE", Map.of(), List.of(file)));
        verify(storage, never()).delete(anyString());
    }

    /** Spring 프록시를 통해 운영 코드와 같은 선언적 트랜잭션을 적용한다. */
    private static CareerItemAttachmentTransactionService transactionalOperations(
            CareerItemAttachmentRepository repository, CareerItemServiceImpl careerItems,
            CareerItemAttachmentStorage storage, PlatformTransactionManager manager) {
        ProxyFactory proxy = new ProxyFactory(new CareerItemAttachmentTransactionService(repository, careerItems, storage));
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        proxy.addAdvice(interceptor);
        return (CareerItemAttachmentTransactionService) proxy.getProxy();
    }
}
