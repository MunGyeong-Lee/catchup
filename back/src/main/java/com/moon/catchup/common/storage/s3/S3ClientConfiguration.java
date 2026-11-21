package com.moon.catchup.common.storage.s3;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** 공통 S3 클라이언트의 리전과 API 호출 제한 시간을 구성한다. */
@Configuration(proxyBeanMethods = false)
public class S3ClientConfiguration {

    /** 기존 리전 설정을 사용해 S3 클라이언트를 만들고 Spring 종료 시 닫는다. */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(@Value("${catchup.attachments.region}") String region) {
        return S3Client.builder().region(Region.of(region))
                .overrideConfiguration(configuration -> configuration
                        .apiCallTimeout(Duration.ofSeconds(60))
                        .apiCallAttemptTimeout(Duration.ofSeconds(20)))
                .build();
    }
}
