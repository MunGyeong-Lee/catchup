package com.moon.catchup.storage.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Stream;

import com.moon.catchup.common.storage.s3.S3ClientConfiguration;
import com.moon.catchup.common.storage.s3.S3ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** 공통 S3 연동을 사용하는 첨부 저장 경로와 정리 범위를 검증한다. */
public class S3SourceAttachmentStorageTest {
    private static final String BUCKET = "catchup-test";
    private static final String PREFIX = "attachments/";
    private static final String ATTACHMENT_KEY = "1e6fbb63-1246-4990-82a0-b18847637500";

    private final S3ObjectStorage s3ObjectStorage = mock(S3ObjectStorage.class);
    private final S3SourceAttachmentStorage attachmentStorage =
            new S3SourceAttachmentStorage(this.s3ObjectStorage, new S3AttachmentSettings(BUCKET, PREFIX));

    /** 기존 첨부 경로와 콘텐츠 유형, 크기, 읽기 범위가 공통 연동에 전달되는지 확인한다. */
    @Test
    public void passesAttachmentPathAndByteRangeToSharedStorage() {
        String objectKey = PREFIX + ATTACHMENT_KEY;
        InputStream content = new ByteArrayInputStream(new byte[] {1, 2, 3});
        InputStream rangedContent = new ByteArrayInputStream(new byte[] {1, 2});
        when(this.s3ObjectStorage.read(BUCKET, objectKey, "bytes=0-1")).thenReturn(rangedContent);

        this.attachmentStorage.put(ATTACHMENT_KEY, "application/pdf", 3, content);
        assertThat(this.attachmentStorage.read(ATTACHMENT_KEY, "bytes=0-1")).isSameAs(rangedContent);
        this.attachmentStorage.delete(ATTACHMENT_KEY);

        verify(this.s3ObjectStorage).put(BUCKET, objectKey, "application/pdf", 3, content);
        verify(this.s3ObjectStorage).delete(BUCKET, objectKey);
    }

    /** 첨부 접두어 바로 아래의 UUID 키만 정리 대상으로 반환하는지 확인한다. */
    @Test
    public void limitsCleanupToManagedAttachmentKeys() {
        when(this.s3ObjectStorage.listKeys(BUCKET, PREFIX)).thenReturn(Stream.of(
                PREFIX + ATTACHMENT_KEY,
                "ai-results/" + ATTACHMENT_KEY,
                PREFIX + "nested/" + ATTACHMENT_KEY,
                PREFIX + "readme.txt",
                PREFIX));

        assertThat(this.attachmentStorage.listKeys()).containsExactly(ATTACHMENT_KEY);
        verify(this.s3ObjectStorage).listKeys(BUCKET, PREFIX);
    }

    /** 버킷을 설정하지 않은 환경에서 S3 호출 없이 빈 목록과 기존 오류를 반환하는지 확인한다. */
    @Test
    public void handlesMissingAttachmentBucketWithoutCallingS3() {
        S3SourceAttachmentStorage unconfiguredStorage =
                new S3SourceAttachmentStorage(this.s3ObjectStorage, new S3AttachmentSettings("", PREFIX));

        assertThat(unconfiguredStorage.listKeys()).isEmpty();
        assertThatThrownBy(() -> unconfiguredStorage.delete(ATTACHMENT_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Attachment bucket is not configured");
        verifyNoInteractions(this.s3ObjectStorage);
    }

    /** 기존 설정 키로 공통 S3 클라이언트와 첨부 저장 어댑터가 구성되는지 확인한다. */
    @Test
    public void wiresSharedClientAndAttachmentSettingsUsingExistingProperties() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("attachmentSettings", Map.of(
                    "catchup.attachments.bucket", BUCKET,
                    "catchup.attachments.prefix", "attachments",
                    "catchup.attachments.region", "ap-southeast-2")));
            context.register(S3ClientConfiguration.class, S3ObjectStorage.class, S3AttachmentConfiguration.class,
                    S3SourceAttachmentStorage.class);
            context.refresh();

            assertThat(context.getBean(SourceAttachmentStorage.class))
                    .isInstanceOf(S3SourceAttachmentStorage.class);
            S3AttachmentSettings settings = context.getBean(S3AttachmentSettings.class);
            assertThat(settings.getBucket()).isEqualTo(BUCKET);
            assertThat(settings.getPrefix()).isEqualTo(PREFIX);
            assertThat(context.getBean(S3Client.class).serviceClientConfiguration().region())
                    .isEqualTo(Region.AP_SOUTHEAST_2);
        }
    }
}
