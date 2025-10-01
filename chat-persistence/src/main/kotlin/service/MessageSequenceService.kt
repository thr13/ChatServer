package service

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

//들어오는 메시지 순서 보장
@Service
class MessageSequenceService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val prefix = "chat:sequence" //키 중복 방지

    fun getNextSequence(chatRoomId: Long): Long {
        val key = "${prefix}${chatRoomId}"

        return redisTemplate.opsForValue().increment(key) ?: 1L
    }
}