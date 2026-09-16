package com.sunmoon.bo.domain.table;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/**
 * 테이블 기준 정보와 QR 코드 발급.
 *
 * <p>코드는 등록할 때 한 번 발급되고 <b>그 뒤로 바뀌지 않습니다</b>. 번호를
 * 고치든 자리를 옮기든 같은 코드입니다 — 그래야 이미 붙여둔 스티커가
 * 계속 그 테이블을 가리킵니다.
 */
@Service
public class StoreTableService {

    /**
     * 사람이 옮겨 적을 일이 없는 값이라(QR이 읽습니다) 길이는 추측
     * 난이도만 보고 정합니다. 10자면 32^10 — 스캔해서 남의 테이블에
     * 주문을 넣으려고 찍어볼 만한 수가 아닙니다.
     */
    private static final int CODE_LENGTH = 10;

    /**
     * 0/O, 1/I/l 처럼 눈으로 헷갈리는 글자를 뺀 Crockford base32.
     * QR이 읽으니 사람이 볼 일은 드물지만, 장애 상황에서 주소를 손으로
     * 옮겨 적어야 할 때가 있고 그때 1분을 아낍니다.
     */
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StoreTableRepository repository;

    public StoreTableService(StoreTableRepository repository) {
        this.repository = repository;
    }

    public List<StoreTable> byStore(String storeId) {
        return repository.findByStore(storeId);
    }

    public Optional<StoreTable> read(long id) {
        return repository.find(id);
    }

    public Optional<StoreTable> byQrCode(String qrCode) {
        return repository.findByQrCode(qrCode);
    }

    public StoreTable create(StoreTableRequest request) {
        return repository.insert(request.toDomain(newCode()));
    }

    /**
     * 저장은 코드를 건드리지 않습니다 — 기존 행에서 읽어 그대로 씁니다.
     * 요청에 코드가 들어올 자리를 두지 않은 것과 같은 이유입니다.
     */
    public boolean save(long id, StoreTableRequest request) {
        return repository.find(id)
                .map(existing -> repository.update(request.toDomain(existing.qrCode())))
                .orElse(false);
    }

    public boolean delete(long id) {
        return repository.delete(id);
    }

    /**
     * 충돌하면 다시 뽑습니다. 유니크 제약이 최종 방어선이고, 여기서 미리
     * 보는 것은 사용자에게 오류 대신 성공을 주기 위해서입니다.
     */
    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = randomCode();
            if (!repository.existsQrCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("QR 코드를 10번 뽑아도 겹쳤습니다 — 코드 길이를 늘려야 합니다");
    }

    private static String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
