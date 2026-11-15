package com.moon.catchup.careeritem.storage;

import java.io.IOException;
import java.io.InputStream;

/** 첨부 원본 저장소가 제공해야 하는 저장, 읽기, 삭제 동작을 정의한다. */
public interface CareerItemAttachmentStorage {
    /** 객체 키와 콘텐츠 유형을 지정해 원본 스트림을 저장한다. */
    void put(String key, String type, long size, InputStream content) throws IOException;
    /** 지정한 바이트 범위나 전체 원본의 입력 스트림을 연다. */
    InputStream read(String key, String range);
    /** 지정한 객체 키의 원본을 제거한다. */
    void delete(String key);
}
