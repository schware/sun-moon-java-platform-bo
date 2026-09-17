package com.sunmoon.bo.domain.master;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 기준정보 표 하나를 정의대로 읽고 씁니다.
 *
 * <p>{@code Map<String, Object>} 인 것이 이 인터페이스의 요점이자
 * 대가입니다. 표마다 record 를 만들면 타입이 지켜 주지만, 그러면
 * 표마다 클래스가 하나씩 늘어나 <b>공통화의 의미가 사라집니다</b>.
 * 대신 안전은 {@link MasterDefinition} 이 봅니다 — 이름은 전부 정의에서
 * 오고, 모르는 칸은 버려집니다.
 */
public interface MasterRepository {

    List<Map<String, Object>> list(MasterDefinition definition, Map<String, String> filters);

    Optional<Map<String, Object>> find(MasterDefinition definition, String id);

    void insert(MasterDefinition definition, Map<String, Object> values);

    void update(MasterDefinition definition, String id, Map<String, Object> values);

    int delete(MasterDefinition definition, String id);

    /** 누가 무엇을 어떻게 바꿨나 (§6.2). 바꾸기 전 값도 함께 적습니다. */
    void audit(String code, String id, String action,
               Map<String, Object> before, Map<String, Object> after, String actor);

    List<Map<String, Object>> auditOf(String code, String id, int limit);
}
