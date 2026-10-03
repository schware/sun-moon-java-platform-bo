plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.sunmoon.bo"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

// BO — sun-moon-java-platform family. Same shape as Order/KDS/Delivery:
// executable JAR, embedded Jetty, one container per service.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    // 결품을 단말에 알리는 길 (menu-events). BO가 단말을 직접 부르지
    // 않는 이유는 Order가 그러지 않는 이유와 같습니다 — 누가 접속해
    // 있는지는 Device Server만 압니다. bo.events.redis=false 면 이
    // starter가 있어도 아무 데도 붙지 않습니다.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // The reason BO is a Spring service at all: session auth, BCrypt and
    // per-action authorization are what this starter is for, and BO is
    // administration - it has no throughput requirement that would argue
    // for hand-rolling them (see the umbrella repo's ADR-0004).
    implementation("org.springframework.boot:spring-boot-starter-security")

    // 로그인 세션을 Redis 에 — 재배포해도 운영자가 로그아웃되지 않게 (2026-10-03).
    // Redis 는 결품 알림(위 starter)으로 이미 쓰고 있어서 새로 붙는 것은 없습니다.
    implementation("org.springframework.session:spring-session-data-redis")

    // 테이블 QR 이미지를 서버에서 그립니다. 인쇄가 목적이라 파일로 저장할
    // 이유가 없고(요청마다 그리면 됩니다), 이 계열에 파일 저장소도 없습니다.
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    implementation("io.micrometer:micrometer-registry-prometheus")

    // Pinned to 2.6.0 for the same reason the sibling services pin it:
    // 2.6.0 is what springdoc's POM declares against Spring Boot 3.3.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.test {
    useJUnitPlatform()
}
