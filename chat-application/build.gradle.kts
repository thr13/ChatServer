plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management")
    kotlin("plugin.jpa")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
    }
}

dependencies {
    // 도메인 모듈 의존성 (DTO와 서비스 인터페이스 사용)
//    implementation(project(":chat-api"))
    implementation(project(":chat-domain"))
//    implementation(project(":chat-persistence"))
//    implementation(project(":chat-websocket"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
}