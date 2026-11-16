package com.moon.catchup.careeritem.storage;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** 첨부 파일의 S3 저장 경로와 클라이언트를 구성한다. */
@Configuration(proxyBeanMethods = false)
public class S3AttachmentConfiguration {

    /** 버킷 이름을 읽고 객체 키 접두어가 슬래시로 끝나도록 정규화한다. */
    @Bean
    public S3AttachmentSettings s3AttachmentSettings(@Value("${catchup.attachments.bucket}") String bucket,
                                              @Value("${catchup.attachments.prefix}") String prefix) {
        return new S3AttachmentSettings(bucket, prefix.endsWith("/") ? prefix : prefix + "/");
    }

    /** 리전과 제한 시간을 적용한 S3 클라이언트를 생성한다. Spring이 종료 시 클라이언트를 닫는다. */
    @Bean(destroyMethod = "close")
    public S3Client s3AttachmentClient(@Value("${catchup.attachments.region}") String region) {
        return S3Client.builder().region(Region.of(region))
                .overrideConfiguration(configuration -> configuration
                        .apiCallTimeout(Duration.ofSeconds(60))
                        .apiCallAttemptTimeout(Duration.ofSeconds(20)))
                .build();
    }
}
