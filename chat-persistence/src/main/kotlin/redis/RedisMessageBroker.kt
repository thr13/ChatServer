package redis

import com.fasterxml.jackson.databind.ObjectMapper
import dto.ChatMessage
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class RedisMessageBroker(
    private val redisTemplate: RedisTemplate<String, String>,
    private val messageListenerContainer: RedisMessageListenerContainer,
    private val objectMapper: ObjectMapper
) : MessageListener {
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
            unsubscribeFromRoom(roomId)
        }
        logger.info("RedisMessageListenerContainer 제거중")
    }

    fun setLocalMessageHandler(handler: (Long, ChatMessage) -> Unit) {
        this.localMessageHandler = handler
    }

    //채팅방 구독
    fun subscribeToRoom(roomId: Long) {
        if (subscribeRooms.add(roomId)) {
            val topic = ChannelTopic("chat.room.$roomId")
            messageListenerContainer.addMessageListener(this, topic)
            logger.info("채팅방 $roomId(번)을 구독하였습니다.")
        } else {
            logger.error("구독할 채팅방 $roomId(번)이 존재하지 않습니다.")
        }
    }

    //채팅방 구독 취소
    fun unsubscribeFromRoom(roomId: Long) {
        if (subscribeRooms.remove(roomId)) {
            val topic = ChannelTopic("chat.room.$roomId")
            messageListenerContainer.removeMessageListener(this, topic)
            logger.info("채팅방 $roomId(번)을 구독 취소 하였습니다.")
        } else {
            logger.error("구독 취소할 채팅방 $roomId(번)이 존재하지 않습니다.")
        }
    }

    fun broadcastToRoom(roomId: Long, message: ChatMessage, excludeServerId: String? = null) {
        try {
            val message = DistributedMessage(
                id = "$serverId-${System.currentTimeMillis()}-${System.nanoTime()}",
                serverId = serverId,
                roomId = roomId,
                excludeServerId = excludeServerId,
                timestamp = LocalDateTime.now(),
                payload = message
            )

            val json = objectMapper.writeValueAsString(message)
            redisTemplate.convertAndSend("chat.room.$roomId", json)

            logger.info("$roomId 에서 $json 로 broadcast 되었습니다.")
        } catch (e: Exception) {
            logger.error("broadcast $roomId 오류", e)
        }
    }

    //메시지를 받았을때 호출
    override fun onMessage(message: Message, pattern: ByteArray?) {
        try {
            val json = String(message.body)
            val distributedMessage = objectMapper.readValue(json, DistributedMessage::class.java)

            if (distributedMessage.excludeServerId == serverId) {
                logger.info("$serverId 는 제외된 서버 ID 입니다.")
                return
            }

            if (processedMessages.containsKey(distributedMessage.id)) {
                logger.info("$serverId 는 중복된 메시지 입니다.")
                return
            }

            localMessageHandler?.invoke(distributedMessage.roomId, distributedMessage.payload)
            processedMessages[distributedMessage.id] = System.currentTimeMillis()

            if (processedMessages.size > 10000) {
                val oldestEntries = processedMessages.entries.sortedBy { it.value }
                    .take(processedMessages.size - 10000)
                oldestEntries.forEach { processedMessages.remove(it.key)}
            }

            logger.info("processedMessages $distributedMessage.id")

        } catch (e: Exception) {
            logger.error("onMessage 오류", e)
        }
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

    data class DistributedMessage(
        val id: String,
        val serverId: String,
        val roomId: Long,
        val excludeServerId: String?,
        val timestamp: LocalDateTime,
        val payload: ChatMessage
    )
}