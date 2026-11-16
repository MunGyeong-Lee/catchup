package com.moon.catchup.careeritem.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.moon.catchup.careeritem.storage.CareerItemAttachmentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** HTTP 요청과 PostgreSQL을 연결해 첨부 등록과 읽기 계약을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CareerItemAttachmentIntegrationTest {
    private static final String CAREER_ITEM_PATH = "/api/career-items";
    private static final byte[] PDF = "%PDF-1.7\nTest document\n".getBytes(StandardCharsets.UTF_8);

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CareerItemAttachmentCleanup cleanup;
    @MockitoBean CareerItemAttachmentStorage storage;
    @MockitoSpyBean CareerItemServiceImpl careerItems;
    @Autowired CareerItemAttachmentRegistrationServiceImpl recovery;

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final HttpClient client = HttpClient.newHttpClient();

    /** 원본 저장, 읽기, 삭제를 재현하는 대체 객체 저장소를 준비한다. */
    @BeforeEach
    void prepareStorage() throws Exception {
        doAnswer(call -> {
            objects.put(call.getArgument(0), call.<InputStream>getArgument(3).readAllBytes());
            return null;
        }).when(storage).put(anyString(), anyString(), anyLong(), any());
        doAnswer(call -> {
            objects.remove(call.getArgument(0));
            return null;
        }).when(storage).delete(anyString());
        when(storage.read(anyString(), nullable(String.class))).thenAnswer(call -> {
            byte[] bytes = objects.get(call.getArgument(0));
            if (bytes == null) throw new IllegalStateException("Object missing");
            String range = call.getArgument(1);
            if (range != null) {
                String[] bounds = range.substring("bytes=".length()).split("-");
                bytes = Arrays.copyOfRange(bytes, Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]) + 1);
            }
            return new ByteArrayInputStream(bytes);
        });
    }

    /** 기존 등록, 목록 응답, 다시 열기, 바이트 범위, 원본 내려받기를 검증한다. */
    @Test
    void keepsExistingRegistrationAndSupportsInlineRangeAndOriginalDownload() throws Exception {
        HttpResponse<byte[]> withoutFiles = send(request(CAREER_ITEM_PATH).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(careerItem("Legacy registration"))));
        assertEquals(201, withoutFiles.statusCode());
        JsonNode originalId = successBody(withoutFiles).get("careerItemId");
        assertTrue(originalId.isIntegralNumber());
        assertTrue(originalId.asLong() > 0);
        String otherCareerItemId = originalId.asText();
        assertEquals("[]", successBody(send(request(CAREER_ITEM_PATH + "/" + otherCareerItemId + "/attachments"))).toString());

        UUID requestId = UUID.randomUUID();
        HttpResponse<byte[]> created = upload(requestId, "Attachments", PDF, PDF);
        assertEquals(201, created.statusCode(), new String(created.body(), StandardCharsets.UTF_8));
        JsonNode addedId = successBody(created).get("careerItemId");
        assertTrue(addedId.isIntegralNumber());
        assertEquals(originalId.asLong() + 1, addedId.asLong());
        String careerItemId = addedId.asText();
        assertArrayEquals(created.body(), upload(requestId, "Attachments", PDF, PDF).body());
        assertEquals(2, objects.size());

        JsonNode summaries = successBody(send(request(CAREER_ITEM_PATH)));
        JsonNode summary = summaries.valueStream()
                .filter(item -> careerItemId.equals(item.get("careerItemId").asText())).findFirst().orElseThrow();
        assertEquals("EXPERIENCE", summary.get("careerItemType").asText());
        assertEquals("Attachments", summary.get("displayName").asText());
        assertFalse(summary.has("historyType"));

        String attachments = CAREER_ITEM_PATH + "/" + careerItemId + "/attachments";
        JsonNode list = successBody(send(request(attachments)));
        assertEquals(2, list.size());
        assertFalse(list.get(0).has("objectKey"));
        String attachmentId = list.get(0).get("attachmentId").asText();
        String file = attachments + "/" + attachmentId;
        HttpResponse<byte[]> inline = send(request(file + "/view"));
        assertEquals(200, inline.statusCode());
        assertEquals("application/pdf", inline.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("nosniff", inline.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertTrue(inline.headers().firstValue("Content-Disposition").orElseThrow().startsWith("inline"));
        assertArrayEquals(PDF, inline.body());

        HttpResponse<byte[]> partial = send(request(file + "/view").header("Range", "bytes=0-4"));
        assertEquals(206, partial.statusCode());
        assertArrayEquals(Arrays.copyOf(PDF, 5), partial.body());
        assertEquals("bytes 0-4/" + PDF.length, partial.headers().firstValue("Content-Range").orElseThrow());
        HttpResponse<byte[]> invalidRange = send(request(file + "/view").header("Range", "bytes=500-501"));
        assertEquals("bytes */" + PDF.length, invalidRange.headers().firstValue("Content-Range").orElseThrow());
        assertError(invalidRange, 416, "INVALID_RANGE");

        HttpResponse<byte[]> download = send(request(file + "/download"));
        assertArrayEquals(PDF, download.body());
        assertTrue(download.headers().firstValue("Content-Disposition").orElseThrow()
                .startsWith("attachment;"));
        assertTrue(download.headers().firstValue("Content-Disposition").orElseThrow()
                .contains("filename*=UTF-8''%ED%95%9C%EA%B8%80.pdf"));
        String wrongParent = CAREER_ITEM_PATH + "/" + otherCareerItemId + "/attachments/" + attachmentId;
        assertError(send(request(wrongParent + "/view")), 404, "ATTACHMENT_NOT_FOUND");
        assertError(send(request(wrongParent + "/download")), 404, "ATTACHMENT_NOT_FOUND");
        objects.clear();
        assertError(send(request(file + "/view")), 503, "ATTACHMENT_STORAGE_UNAVAILABLE");
    }

    /** 잘못된 파일과 일부 업로드 실패 시 자료가 공개되지 않는지 확인한다. */
    @Test
    void invalidFileAndPartialUploadDoNotPublishCareerItem() throws Exception {
        UUID invalid = UUID.randomUUID();
        HttpResponse<byte[]> rejected = upload(invalid, "Invalid attachment", PDF, "<html>".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, rejected.statusCode());
        JsonNode error = mapper.readTree(rejected.body());
        assertEquals("VALIDATION_FAILED", error.path("error").asText());
        assertFalse(error.path("error_message").asText().isBlank());
        assertTrue(error.path("response").isNull());
        assertTrue(error.path("field_errors").has("attachments"));
        assertTrue(objects.isEmpty());

        UUID failed = UUID.randomUUID();
        doAnswer(call -> {
            if (!objects.isEmpty()) throw new IOException("Second object failed");
            objects.put(call.getArgument(0), call.<InputStream>getArgument(3).readAllBytes());
            return null;
        }).when(storage).put(anyString(), anyString(), anyLong(), any());
        assertEquals(503, upload(failed, failed.toString(), PDF, PDF).statusCode());
        assertTrue(objects.isEmpty());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM career_items WHERE details ->> 'title' = ?", Integer.class, failed.toString()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM upload_attempts WHERE request_id = ?", Integer.class, failed));
    }

    /** 즉시 정리가 실패하면 나중에 처리할 객체 키가 남는지 확인한다. */
    @Test
    void failedCleanupLeavesKeysForRecovery() throws Exception {
        UUID requestId = UUID.randomUUID();
        doAnswer(call -> { throw new IOException("Upload failed"); })
                .when(storage).put(anyString(), anyString(), anyLong(), any());
        doAnswer(call -> { throw new IllegalStateException("Cleanup failed"); }).when(storage).delete(anyString());
        assertEquals(503, upload(requestId, requestId.toString(), PDF).statusCode());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM upload_attempts WHERE request_id = ?", Integer.class, requestId));
        jdbc.update("UPDATE upload_attempts SET created_at = now() - interval '10 minutes' WHERE request_id = ?",
                requestId);
        doAnswer(call -> null).when(storage).delete(anyString());
        // The recovery pass uses its own transaction and checks the durable attempt again under a row lock.
        recovery.recoverAbandonedAttempts();
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM upload_attempts WHERE request_id = ?", Integer.class, requestId));
    }

    /** 자료 저장 실패가 데이터베이스에 반영되지 않고 전송한 원본이 정리되는지 확인한다. */
    @Test
    void databaseFailureRollsBackCareerItemAndRemovesUploadedObject() throws Exception {
        UUID requestId = UUID.randomUUID();
        doAnswer(call -> {
            call.callRealMethod();
            throw new DataAccessResourceFailureException("Injected failure after inserting careerItem");
        }).when(careerItems).insert(argThat(details -> requestId.toString().equals(details.getFields().get("title"))));

        assertEquals(503, upload(requestId, requestId.toString(), PDF).statusCode());
        assertTrue(objects.isEmpty());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM career_items WHERE details ->> 'title' = ?", Integer.class, requestId.toString()));
    }

    /** 같은 요청을 동시에 등록해도 동일한 자료 식별자를 돌려주는지 확인한다. */
    @Test
    void concurrentSubmissionsWithSameIdentifierReturnOneCareerItem() throws Exception {
        UUID requestId = UUID.randomUUID();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<HttpResponse<byte[]>> first = executor.submit(() -> upload(requestId, requestId.toString(), PDF));
            Future<HttpResponse<byte[]>> second = executor.submit(() -> upload(requestId, requestId.toString(), PDF));
            HttpResponse<byte[]> firstResponse = first.get(15, TimeUnit.SECONDS);
            HttpResponse<byte[]> secondResponse = second.get(15, TimeUnit.SECONDS);
            assertEquals(201, firstResponse.statusCode());
            assertEquals(201, secondResponse.statusCode());
            assertArrayEquals(firstResponse.body(), secondResponse.body());
        }
        assertEquals(1, objects.size());
    }

    /** 여러 개의 작은 파일을 한 요청에서 등록할 수 있는지 확인한다. */
    @Test
    void multipartParserDoesNotImposeItsDefaultFileCountLimit() throws Exception {
        byte[][] copies = new byte[100][];
        Arrays.fill(copies, PDF);
        HttpResponse<byte[]> result = upload(UUID.randomUUID(), "Many small attachments", copies);
        assertEquals(201, result.statusCode(), new String(result.body(), StandardCharsets.UTF_8));
        assertEquals(100, objects.size());
    }

    /** 자료 입력 오류를 첨부 여부와 관계없이 같은 필드 오류로 반환하는지 확인한다. */
    @Test
    void missingTitleUsesTheSameFieldErrorWithOrWithoutAttachments() throws Exception {
        HttpResponse<byte[]> plain = send(request(CAREER_ITEM_PATH).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(careerItem(" "))));
        HttpResponse<byte[]> multipart = upload(UUID.randomUUID(), " ", PDF);
        assertEquals(400, plain.statusCode());
        assertEquals(400, multipart.statusCode());
        JsonNode error = mapper.readTree(plain.body());
        assertEquals(error, mapper.readTree(multipart.body()));
        assertEquals("VALIDATION_FAILED", error.path("error").asText());
        assertTrue(error.path("field_errors").has("title"));
        assertTrue(objects.isEmpty());
    }

    /** 파싱 실패와 업로드 제한 초과가 항목 오류 없이 공통 응답을 반환하는지 확인한다. */
    @Test
    void malformedRequestAndOversizeUploadUseCommonErrors() throws Exception {
        assertError(send(request(CAREER_ITEM_PATH).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{"))), 400, "INVALID_REQUEST");
        assertError(upload(UUID.randomUUID(), "Oversize attachment", new byte[20 * 1024 * 1024 + 1]),
                413, "PAYLOAD_TOO_LARGE");
        assertError(upload("invalid-request-id", "Invalid id", PDF), 400, "INVALID_REQUEST");
    }

    /** 정상 응답에 공통 필드가 포함되고 개별 입력 오류 필드가 생략되는지 확인한다. */
    private JsonNode successBody(HttpResponse<byte[]> response) throws Exception {
        JsonNode body = mapper.readTree(response.body());
        assertTrue(body.path("error").isNull());
        assertTrue(body.path("error_message").isNull());
        assertFalse(body.has("field_errors"));
        return body.get("response");
    }

    /** 입력 검증 외 오류의 HTTP 상태와 공통 응답 필드를 확인한다. */
    private void assertError(HttpResponse<byte[]> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode());
        JsonNode body = mapper.readTree(response.body());
        assertEquals(code, body.path("error").asText());
        assertFalse(body.path("error_message").asText().isBlank());
        assertTrue(body.path("response").isNull());
        assertFalse(body.has("field_errors"));
    }

    /** 문서 인터페이스의 설명이 실제 OpenAPI 문서에 포함되는지 확인한다. */
    @Test
    void publishesControllerDocumentationFromDocsInterfaces() throws Exception {
        HttpResponse<byte[]> response = send(request("/v3/api-docs"));
        assertEquals(200, response.statusCode());
        JsonNode paths = mapper.readTree(response.body()).path("paths");
        assertEquals("자료 목록 조회", paths.path(CAREER_ITEM_PATH).path("get").path("summary").asText());
        assertEquals("첨부 원본 내려받기", paths.path(CAREER_ITEM_PATH + "/{careerItemId}/attachments/{attachmentId}/download")
                .path("get").path("summary").asText());
    }

    /** 자료 변경 실패가 기존 자료와 첨부 원본을 유지하는지 확인한다. */
    @Test
    void failedEditKeepsExistingAttachment() throws Exception {
        String path = createdWithAttachment("Before edit");
        String removed = successBody(send(request(path + "/attachments"))).get(0).get("attachmentId").asText();
        String body = """
                {"careerItemType":"CERTIFICATION","details":{},"removeAttachmentIds":["%s"]}
                """.formatted(removed);
        HttpResponse<byte[]> invalid = send(request(path).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
        assertEquals(400, invalid.statusCode());
        JsonNode invalidBody = mapper.readTree(invalid.body());
        assertEquals("VALIDATION_FAILED", invalidBody.path("error").asText());
        assertTrue(invalidBody.path("field_errors").has("name"));
        assertEquals("Before edit", successBody(send(request(path))).get("details").get("title").asText());
        assertEquals(200, send(request(path + "/attachments/" + removed + "/view")).statusCode());
    }

    /** 저장소 오류가 아니라 데이터베이스 삭제 오류일 때 자료와 원본을 모두 유지한다. */
    @Test
    void failedDatabaseDeleteKeepsAttachment() throws Exception {
        String path = createdWithAttachment("Before delete");
        long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        this.jdbc.update("INSERT INTO upload_attempts(request_id, object_keys, career_item_id) VALUES (?, '', ?)", UUID.randomUUID(), id);
        this.jdbc.execute("CREATE TABLE edit_delete_guard (id bigint REFERENCES career_items(career_item_id))");
        try {
            this.jdbc.update("INSERT INTO edit_delete_guard VALUES (?)", id);
            assertError(send(request(path).DELETE()), 500, "INTERNAL_ERROR");
            assertEquals(1, successBody(send(request(path + "/attachments"))).size());
            assertEquals(1, objects.size());
        } finally {
            this.jdbc.execute("DROP TABLE edit_delete_guard");
        }
    }

    /** 원본 삭제 실패를 일일 대조에서 복구하며 예약된 원본은 유지하는지 확인한다. */
    @Test
    void reconcilesFailedDeletesAndProtectsPendingUpload() throws Exception {
        String path = createdWithAttachment("Cleanup sample");
        String removed = objects.keySet().iterator().next();
        String pending = UUID.randomUUID().toString();
        this.jdbc.update("INSERT INTO upload_attempts(request_id, object_keys) VALUES (?, ?)", UUID.randomUUID(), pending);
        objects.put(pending, PDF);
        CountDownLatch attempted = new CountDownLatch(1);
        doAnswer(call -> {
            attempted.countDown();
            throw new IllegalStateException("Injected storage failure");
        }).when(storage).delete(removed);
        assertEquals(200, send(request(path).DELETE()).statusCode());
        assertTrue(attempted.await(5, TimeUnit.SECONDS));
        assertEquals(404, send(request(path)).statusCode());
        assertTrue(objects.containsKey(removed));
        doAnswer(call -> {
            objects.remove(removed);
            return null;
        }).when(storage).delete(removed);
        when(storage.listKeys()).thenReturn(List.copyOf(objects.keySet()));
        cleanup.reconcile();
        assertFalse(objects.containsKey(removed));
        assertTrue(objects.containsKey(pending));
    }

    /** 실제 수정 쓰기 오류가 함께 삭제한 첨부 행을 복구하는지 확인한다. */
    @Test
    void failedUpdateRollsBackAttachmentRemoval() throws Exception {
        String path = createdWithAttachment("Before failed write");
        String removed = successBody(send(request(path + "/attachments"))).get(0).get("attachmentId").asText();
        this.jdbc.execute("""
                ALTER TABLE career_items ADD CONSTRAINT reject_edit_title
                CHECK (details->>'title' <> 'Rejected update')
                """);
        try {
            String body = """
                    {"careerItemType":"EXPERIENCE","details":{"title":"Rejected update","contribution":"Sample"},
                     "removeAttachmentIds":["%s"]}
                    """.formatted(removed);
            assertError(send(request(path).header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))), 500, "INTERNAL_ERROR");
            assertEquals("Before failed write", successBody(send(request(path))).get("details").get("title").asText());
            assertEquals(200, send(request(path + "/attachments/" + removed + "/download")).statusCode());
            assertEquals(1, objects.size());
        } finally {
            this.jdbc.execute("ALTER TABLE career_items DROP CONSTRAINT reject_edit_title");
        }
    }

    /** 자료 삭제가 완료된 등록 요청을 재전송해도 자료가 다시 생성되지 않는지 확인한다. */
    @Test
    void replayAfterDeletionDoesNotRecreateCareerItem() throws Exception {
        UUID requestId = UUID.randomUUID();
        HttpResponse<byte[]> created = upload(requestId, "Deleted upload", PDF);
        assertEquals(201, created.statusCode());
        String path = CAREER_ITEM_PATH + "/" + successBody(created).get("careerItemId").asText();
        assertEquals(200, send(request(path).DELETE()).statusCode());
        assertError(upload(requestId, "Deleted upload", PDF), 404, "CAREER_ITEM_NOT_FOUND");
        assertError(send(request(path)), 404, "CAREER_ITEM_NOT_FOUND");
    }

    /** 파일 추가와 제거를 함께 저장하고 같은 수정 요청을 다시 실행하지 않는다. */
    @Test
    void multipartEditPreservesIdentityAndReusesCompletedAttempt() throws Exception {
        String path = createdWithAttachment("Before multipart edit");
        long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        String originalCreatedAt = this.jdbc.queryForObject(
                "SELECT created_at::text FROM career_items WHERE career_item_id = ?", String.class, id);
        String removed = successBody(send(request(path + "/attachments"))).get(0).get("attachmentId").asText();
        String update = """
                {"careerItemType":"CERTIFICATION","details":{"name":"Updated item","issuedOn":"2025-01-01"},
                 "removeAttachmentIds":["%s"]}
                """.formatted(removed);
        String requestId = UUID.randomUUID().toString();
        assertEquals(200, multipart(path, "PUT", requestId, update, PDF).statusCode());
        assertEquals(200, multipart(path, "PUT", requestId, update, PDF).statusCode());
        JsonNode item = successBody(send(request(path)));
        assertEquals(id, item.path("careerItemId").asLong());
        assertEquals("CERTIFICATION", item.path("careerItemType").asText());
        assertEquals(1, item.path("attachments").size());
        assertFalse(removed.equals(item.path("attachments").get(0).path("attachmentId").asText()));
        assertEquals(originalCreatedAt, this.jdbc.queryForObject(
                "SELECT created_at::text FROM career_items WHERE career_item_id = ?", String.class, id));
    }

    /** 일일 대조가 실제 업로드 도중의 원본과 완료된 원본을 모두 보호한다. */
    @Test
    void dailyScanPreservesActiveUploadAcrossCommit() throws Exception {
        CountDownLatch objectWritten = new CountDownLatch(1);
        CountDownLatch finishUpload = new CountDownLatch(1);
        doAnswer(call -> {
            objects.put(call.getArgument(0), call.<InputStream>getArgument(3).readAllBytes());
            objectWritten.countDown();
            assertTrue(finishUpload.await(5, TimeUnit.SECONDS));
            return null;
        }).when(storage).put(anyString(), anyString(), anyLong(), any());
        when(storage.listKeys()).thenAnswer(call -> List.copyOf(objects.keySet()));
        try (ExecutorService clientThread = Executors.newSingleThreadExecutor()) {
            Future<HttpResponse<byte[]>> uploading = clientThread.submit(() -> upload(UUID.randomUUID(), "Active upload", PDF));
            try {
                assertTrue(objectWritten.await(5, TimeUnit.SECONDS));
                cleanup.reconcile();
                assertEquals(1, objects.size());
            } finally {
                finishUpload.countDown();
            }
            assertEquals(201, uploading.get(5, TimeUnit.SECONDS).statusCode());
        }
        cleanup.reconcile();
        assertEquals(1, objects.size());
    }

    /** 저장 시각이 같으면 자료 식별자가 큰 항목을 먼저 반환한다. */
    @Test
    void listsLatestAndBreaksTiesById() throws Exception {
        List<Long> expected = this.jdbc.queryForList("""
                INSERT INTO career_items(career_item_type, details, created_at) VALUES
                    ('EXPERIENCE', '{"title":"Older sort sample"}', '2020-01-01T00:00:00Z'),
                    ('EXPERIENCE', '{"title":"First tied sort sample"}', '2020-01-02T00:00:00Z'),
                    ('EXPERIENCE', '{"title":"Second tied sort sample"}', '2020-01-02T00:00:00Z')
                RETURNING career_item_id
                """, Long.class).reversed();
        List<Long> actual = successBody(send(request(CAREER_ITEM_PATH))).valueStream()
                .map(item -> item.path("careerItemId").asLong()).filter(expected::contains).toList();
        assertEquals(expected, actual);
    }

    /** 원본이 있는 자료를 만들고 자료 요청 경로를 반환한다. */
    private String createdWithAttachment(String title) throws Exception {
        return CAREER_ITEM_PATH + "/" + successBody(upload(UUID.randomUUID(), title, PDF)).get("careerItemId").asText();
    }

    /** 자료와 표본 파일을 다중 부분 요청으로 제출한다. */
    private HttpResponse<byte[]> upload(UUID requestId, String title, byte[]... files) throws Exception {
        return upload(requestId.toString(), title, files);
    }

    /** 요청 식별자 문자열을 그대로 전송해 정상 요청과 잘못된 식별자를 검증한다. */
    private HttpResponse<byte[]> upload(String requestId, String title, byte[]... files) throws Exception {
        return multipart(CAREER_ITEM_PATH, "POST", requestId, careerItem(title), files);
    }

    /** 등록 또는 수정의 다중 부분 요청을 같은 형식으로 전송한다. */
    private HttpResponse<byte[]> multipart(String path, String method, String requestId, String details,
                                           byte[]... files) throws Exception {
        String boundary = "catchup-test-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"careerItem\"\r\n"
                + "Content-Type: application/json\r\n\r\n" + details + "\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"
                + requestId + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (byte[] file : files) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"한글.pdf\"\r\n"
                    + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(file);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
    }

    /** 경험 자료 테스트에 사용할 등록 요청 본문을 만든다. */
    private static String careerItem(String title) {
        return "{\"careerItemType\":\"EXPERIENCE\",\"details\":{\"title\":\"" + title
                + "\",\"contribution\":\"Example contribution\"}}";
    }

    /** 테스트 서버의 지정 경로에 요청을 구성한다. */
    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }

    /** HTTP 요청을 보내고 응답 본문을 바이트로 반환한다. */
    private HttpResponse<byte[]> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
