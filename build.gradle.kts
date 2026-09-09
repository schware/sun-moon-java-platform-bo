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

    // The reason BO is a Spring service at all: session auth, BCrypt and
    // per-action authorization are what this starter is for, and BO is
    // administration - it has no throughput requirement that would argue
    // for hand-rolling them (see the umbrella repo's ADR-0004).
    implementation("org.springframework.boot:spring-boot-starter-security")

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
