package com.chat.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(
    scanBasePackages = ["com.chat.application"]
    // TODO -> 모듈 추가시 스캔 범위 추가
)

@EnableJpaAuditing //JPA 감사 시능
//@EnableJpaRepositories(basePackages = ["com.chat.persistence.repository"]) //사용 레포지토리 명시
@EntityScan(basePackages = ["com.chat.domain.model"])
class ChatApplication

fun main(args: Array<String>) {
    runApplication<ChatApplication>(*args)
}