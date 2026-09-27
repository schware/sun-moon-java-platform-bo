package com.sunmoon.bo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The React app routes on the client, so a refresh on {@code /bo/board}
 * arrives at the server as a real request for a file that does not exist.
 * These forwards hand those paths back to {@code index.html} and let the
 * router sort it out.
 *
 * <p>Listed one by one rather than matched by wildcard: a catch-all would
 * also swallow genuine 404s from the API, turning "no such endpoint" into
 * a page of HTML. That is why the API lives under {@code /api} — the
 * screen path {@code /board} and the endpoint {@code GET /api/board} can
 * then both exist without either shadowing the other.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 목록은 SpaRoutes 한 곳입니다. 여기와 SecurityConfig 가 각자
        // 들고 있을 때 두 번 어긋났습니다 — /menus·/stores(2026-09-10~15),
        // POS 운영 네 화면과 기준정보(2026-09-17~27). 둘 다 눌러서
        // 들어가면 멀쩡하고 새로고침에서만 죽어서 한참 몰랐습니다.
        for (String route : SpaRoutes.ROUTES) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
