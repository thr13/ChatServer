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
    //Spring JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    //페이징 Spring Data Commons
    implementation("org.springframework.boot:spring-boot-starter-validation")

    //Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}