plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
    }
}

dependencies {
    //도메인 모듈 의존성
    implementation(project(":chat-domain"))

    //JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    //Redis 캐시 및 Pub/Sub
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    //WebSocket
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    //Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    //DB Driver
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
}