package com.sunmoon.bo.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 화면이 가진 주소가 서버에 전부 있는가.
 *
 * <p><b>같은 사고가 두 번 났습니다.</b> 화면({@code App.tsx})에 주소를
 * 더하고 서버 목록을 잊어서, 눌러서 들어가면 멀쩡한데 새로고침에서만
 * 죽는 화면이 생겼습니다 — 2026-09-10에 닷새, 2026-09-17에 열흘. 둘 다
 * 사람이 새로고침을 누른 날에야 드러났습니다.
 *
 * <p>이 시험은 Java 에서 TypeScript 파일을 읽습니다. 언어를 넘는 것이
 * 어색하지만, <b>어긋나는 두 곳이 바로 그 두 언어</b>라서 이 자리
 * 말고는 둘을 한 번에 볼 곳이 없습니다.
 */
class SpaRoutesTest {

    private static final Path APP = Path.of("frontend", "src", "App.tsx");

    /** {@code <Route path="/pos-live" ...>} 의 경로. */
    private static final Pattern ROUTE = Pattern.compile("<Route\\s+path=\"([^\"]+)\"");

    @Test
    void 화면의_모든_주소가_서버에_있다() throws Exception {
        String app = Files.readString(APP, StandardCharsets.UTF_8);

        Set<String> clientRoutes = new LinkedHashSet<>();
        Matcher m = ROUTE.matcher(app);
        while (m.find()) {
            String path = m.group(1);
            // "*" 는 없는 주소를 받는 자리, "/" 는 정적 파일로 이미 열려
            // 있습니다. 둘 다 서버 목록의 일이 아닙니다.
            if (path.equals("*") || path.equals("/")) {
                continue;
            }
            // React Router 의 :code 는 Spring 의 {code} 입니다.
            clientRoutes.add(path.replaceAll(":([A-Za-z]+)", "{$1}"));
        }

        assertThat(clientRoutes)
                .as("App.tsx 에서 경로를 하나도 못 찾았습니다 — 파일 모양이 바뀌었으면 이 시험도 고쳐야 합니다")
                .isNotEmpty();

        assertThat(SpaRoutes.ROUTES)
                .as("화면에 있는 주소가 SpaRoutes 에 없으면 그 화면은 새로고침에서 죽습니다")
                .containsAll(clientRoutes);
    }

    @Test
    void 보안_모양은_변수_자리를_한_단계만_연다() {
        assertThat(SpaRoutes.securityPatterns()).contains("/master/*", "/reprocess");
        assertThat(SpaRoutes.securityPatterns()).doesNotContain("/master/**");
    }
}
