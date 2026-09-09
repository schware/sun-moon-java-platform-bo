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
        for (String route : new String[] {"/board", "/common-code", "/devices"}) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
