package com.moon.catchup.storage.source;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 첨부 파일의 S3 저장 경로를 구성한다. */
@Configuration(proxyBeanMethods = false)
public class S3AttachmentConfiguration {

    /**
     * 버킷 이름을 읽고 객체 키 접두어가 슬래시로 끝나도록 정규화한다.
     *
     * @param bucket 첨부 원본의 S3 버킷 이름
     * @param prefix S3 버킷 내부 첨부 경로의 접두어
     * @return 첨부 버킷과 슬래시로 끝나는 경로 접두어
     */
    @Bean
    public S3AttachmentSettings s3AttachmentSettings(
            @Value("${catchup.attachments.bucket}") String bucket,
            @Value("${catchup.attachments.prefix}") String prefix) {
        return new S3AttachmentSettings(bucket, prefix.endsWith("/") ? prefix : prefix + "/");
    }
}
