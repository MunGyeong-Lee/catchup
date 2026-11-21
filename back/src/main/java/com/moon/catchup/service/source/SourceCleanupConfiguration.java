package com.moon.catchup.service.source;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 소스 또는 첨부 삭제가 DB에 커밋된 뒤, S3 원본 삭제 작업을 요청과 별도로 실행할 실행기를 등록한다.
 * 실행기가 작업을 받지 못하면 {@link SourceAttachmentCleanup}이 경고를 기록하고,
 * 남은 원본은 일일 정리 작업에서 다시 확인한다.
 */
@Configuration(proxyBeanMethods = false)
public class SourceCleanupConfiguration {
    /**
     * 원본 삭제 작업을 한 번에 1건씩 실행하고, 최대 100건을 대기시킨다.
     * 대기열이 가득 차면 새 작업을 거절한다. 한 작업에는 여러 원본 파일이 포함될 수 있다.
     *
     * @return Spring 종료 시 shutdown이 호출되는 첨부 원본 삭제 전용 실행기
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService attachmentCleanupExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));
    }
}
