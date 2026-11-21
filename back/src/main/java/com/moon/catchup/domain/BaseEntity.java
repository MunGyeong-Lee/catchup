package com.moon.catchup.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** DB가 생성하는 공통 등록 시각을 매핑한다. */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseEntity {
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
