package com.sunmoon.bo.transport.http;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// "/" has no natural mapping in a REST-only service — redirect it
// somewhere useful instead of showing Spring Boot's default Whitelabel
// 404, the same way the sibling services do. See Debian-Setting's
// docs/docker.md for the nginx landing page that links all of them.
@Controller
public class RootRedirectController {

    @GetMapping("/")
    public String redirectToSwaggerUi() {
        return "redirect:/swagger-ui/index.html";
    }
}
