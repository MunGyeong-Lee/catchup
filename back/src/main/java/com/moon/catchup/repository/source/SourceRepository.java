package com.moon.catchup.repository.source;

import java.util.Optional;

import com.moon.catchup.domain.source.SourceDetailsVo;
import com.moon.catchup.domain.source.SourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

/** 소스의 저장, 조회, 행 잠금과 목록 표시 정보 조회를 담당한다. */
public interface SourceRepository extends JpaRepository<SourceEntity, Long> {
    /**
     * 검증된 소스를 저장하고 DB에서 발급한 식별자를 돌려준다.
     *
     * @param details 유형별 상세 입력
     * @return 소스 엔티티
     */
    public default SourceEntity insert(SourceDetailsVo details) {
        return save(details.convertToSourceEntity());
    }

    /**
     * 쓰기 작업에서는 소스 행을 잠그고 읽기 작업에서는 잠금 없이 조회한다.
     *
     * @param id 소스 식별자
     * @param lock 쓰기 잠금을 획득할지 여부
     * @return 조회한 소스 엔티티 또는 일치하는 행이 없으면 빈 값
     */
    public default Optional<SourceEntity> find(long id, boolean lock) {
        return lock ? findForUpdate(id) : findById(id);
    }

    /**
     * 현재 트랜잭션에 소스 행의 쓰기 잠금을 획득한다.
     *
     * <p>PostgreSQL의 {@code FOR UPDATE}로 같은 소스의 수정과 삭제를 직렬화한다.
     * 호출자는 쓰기 트랜잭션이 끝날 때까지 잠금을 유지한다.
     *
     * @param id 소스 식별자
     * @return 조회한 소스 엔티티 또는 일치하는 행이 없으면 빈 값
     */
    @NativeQuery("SELECT * FROM sources WHERE source_id = :id FOR UPDATE")
    public Optional<SourceEntity> findForUpdate(@Param("id") long id);

    /**
     * 잠긴 소스의 필드가 실제로 달라졌을 때만 같은 트랜잭션에서 저장한다.
     *
     * @param id 현재 트랜잭션이 잠근 소스 식별자
     * @param details 유형별 상세 입력
     * @return 소스 필드가 바뀌었으면 true
     */
    public default boolean update(long id, SourceDetailsVo details) {
        Optional<SourceEntity> source = findById(id);
        if (source.isEmpty() || !source.get().updateDetails(details)) {
            return false;
        }
        flush();
        return true;
    }

    /**
     * 이미 잠근 소스의 수정 시각을 실제 변경이 일어난 트랜잭션에서 기록한다.
     *
     * <p>잠금을 기다린 시간을 반영하기 위해 DB 트랜잭션 시작 시각 대신
     * PostgreSQL의 현재 시각을 사용한다.
     *
     * @param id 현재 트랜잭션이 잠근 소스 식별자
     */
    @Modifying
    @NativeQuery("UPDATE sources SET updated_at = clock_timestamp() WHERE source_id = :id")
    public void updateTimestamp(@Param("id") long id);

    /**
     * 첨부 및 완료 요청과 연결을 정리한 소스 행을 삭제한다.
     *
     * @param id 소스 식별자
     */
    @Modifying
    @NativeQuery("DELETE FROM sources WHERE source_id = :id")
    public void delete(@Param("id") long id);
}
