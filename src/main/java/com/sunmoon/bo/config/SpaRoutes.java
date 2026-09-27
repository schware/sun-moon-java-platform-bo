package com.sunmoon.bo.config;

import java.util.List;

/**
 * 화면이 가진 주소 — <b>한 곳</b>입니다 (2026-09-27).
 *
 * <p>React 가 주소를 스스로 나누므로, {@code /bo/pos-live} 에서 새로고침을
 * 하면 서버에는 없는 파일을 달라는 요청이 옵니다. 그걸 {@code index.html}
 * 로 돌려주는 것({@link WebConfig})과, 로그인 전에도 그 껍데기를 내주는
 * 것({@link SecurityConfig})이 <b>같은 목록</b>을 알아야 합니다.
 *
 * <p>예전에는 두 클래스가 각자 목록을 들고 있었고, 화면의 {@code App.tsx}
 * 가 세 번째였습니다. <b>세 벌이 따로 놀아서 같은 사고가 두 번</b> 났습니다:
 *
 * <ul>
 *   <li>2026-09-10~15 — {@code /menus} · {@code /stores} 가 새로고침에서만 404.
 *   <li>2026-09-17~27 — POS 운영 네 화면과 기준정보가 새로고침에서 401.
 *       열흘 동안 아무도 몰랐습니다. 눌러서 들어가면 멀쩡해서입니다.
 * </ul>
 *
 * <p>그래서 서버 쪽 둘은 이 목록 하나를 보고, 화면 쪽과 어긋나면
 * {@code SpaRoutesTest} 가 깨집니다. 새 화면을 만들면 <b>여기 한 줄</b>
 * 입니다 — 안 넣으면 시험이 알려줍니다.
 *
 * <h2>와일드카드로 잡지 않는 이유</h2>
 *
 * <p>{@code /**} 로 전부 {@code index.html} 에 보내면 API 의 진짜 404 까지
 * 삼켜서, "그런 주소 없음" 이 HTML 한 페이지가 됩니다. 하나씩 적는 것이
 * 번거로운 대신 그 구분이 남습니다 — 그리고 번거로움은 시험이 덜어 줍니다.
 */
public final class SpaRoutes {

    /**
     * Spring 의 주소 모양으로 적습니다 — 변수는 {@code {code}}.
     * 화면(React Router)의 {@code :code} 와 같은 자리입니다.
     */
    public static final List<String> ROUTES = List.of(
            "/board", "/common-code", "/devices", "/menus", "/stores",
            "/sales", "/app-sales", "/terminals", "/operators", "/tables", "/sold-out",
            // POS 운영 (2026-09-17~18) — 열흘 동안 여기에 없었습니다.
            "/pos-business", "/pos-live", "/pos-deploy", "/pos-notices",
            // 기준정보 공통 CRUD (umbrella docs/adr/0014)
            "/master/{code}",
            // 재처리 (2026-09-27)
            "/reprocess");

    private SpaRoutes() {
    }

    /**
     * 보안 설정이 쓰는 모양 — 변수 자리를 {@code *} 로.
     *
     * <p>{@code *} 는 <b>한 단계</b>만 받습니다. {@code /master/COMPANY} 는
     * 되고 {@code /master/COMPANY/anything} 은 안 됩니다 — 화면에 그런
     * 주소가 없으니 안 되는 것이 맞습니다.
     */
    public static String[] securityPatterns() {
        return ROUTES.stream()
                .map(route -> route.replaceAll("\\{[^}]+}", "*"))
                .toArray(String[]::new);
    }
}
