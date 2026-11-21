package com.moon.catchup.storage.source;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** 첨부 원본 저장소가 제공해야 하는 저장, 읽기, 삭제 동작을 정의한다. */
public interface SourceAttachmentStorage {
    /**
     * 객체 키와 콘텐츠 유형을 지정해 원본 스트림을 저장한다.
     *
     * @param key S3 객체 키
     * @param type 첨부 원본의 콘텐츠 유형
     * @param size 원본의 바이트 수
     * @param content 읽을 원본 스트림
     * @throws IOException 저장할 원본 스트림을 읽을 수 없는 경우
     */
    public void put(String key, String type, long size, InputStream content) throws IOException;
    /**
     * 지정한 바이트 범위나 전체 원본의 입력 스트림을 연다.
     *
     * @param key S3 객체 키
     * @param range 단일 HTTP Range 헤더 값. 전체 요청이면 null
     * @return 호출자가 사용 후 닫아야 하는 원본 입력 스트림
     */
    public InputStream read(String key, String range);
    /**
     * 지정한 객체 키의 원본을 제거한다.
     *
     * @param key S3 객체 키
     */
    public void delete(String key);
    /**
     * 전용 경로 안의 애플리케이션 원본 키를 모두 조회한다.
     *
     * @return 조회 또는 삭제한 S3 객체 키 목록
     */
    public List<String> listKeys();
}
