package redis

import com.fasterxml.jackson.databind.ObjectMapper
import dto.ChatMessage
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class RedisMessageBroker(
    private val redisTemplate: RedisTemplate<String, String>,
    private val messageListenerContainer: RedisMessageListenerContainer,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(RedisMessageBroker::class.java) //고유서비스 값
    private val serverId = System.getenv("HOSTNAME") ?: "server-${System.currentTimeMillis()}" //고유서비스 값
    private val processedMessages = ConcurrentHashMap<String, Long>() //중복 메시지 방지 - 동시성 처리를 위한 ConcurrentHashMap
    private val subscribeRooms = ConcurrentHashMap.newKeySet<Long>()
    private var localMessageHandler: ((Long, ChatMessage) -> Unit) ?= null //Redis 에서 메시지를 받았을떄 호출되어 로컬세션에 전달

    fun getServerId() = serverId

    @PostConstruct
    fun initialize() {
        logger.info("RedisMessageListener 컨테이너 초기화 중")

        Thread {
            try {
                Thread.sleep(30000)
                cleanUpProcessedMessages()
            } catch (e : Exception) {
                logger.error("RedisMessageListener 컨테이너 초기화 중 오류발생", e)
            }
        }.apply {
            isDaemon = true
            name = "redis-broker-cleanup"
            start()
        }
    }

    @PreDestroy
    fun cleanUp() {
        subscribeRooms.forEach { roomId ->
            //TODO 구독취소기능 호출
        }
        logger.info("RedisMessageListenerContainer 제거중")
    }

    fun setLocalMessageHandler(handler: (Long, ChatMessage) -> Unit) {
        this.localMessageHandler = handler
    }

    private fun cleanUpProcessedMessages() {
        val now = System.currentTimeMillis()
        val expiredKeys = processedMessages.filter { (_, time) ->
            now - time > 60000 //1분
        }.keys

        expiredKeys.forEach {
            processedMessages.remove(it)
        }

        if (expiredKeys.isNotEmpty()) {
            logger.info("Redis 에서 ${processedMessages.size} messages 가 제거되었습니다")
        }

    }

}