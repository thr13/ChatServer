package service

import com.fasterxml.jackson.databind.ObjectMapper
import dto.ChatMessage
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import redis.RedisMessageBroker
import repository.ChatRoomMemberRepository
import java.util.concurrent.ConcurrentHashMap

//직접적인 클라이언트와 세션 관리 클래스 -> 세션은 서버마다 다른 값을 가지므로 공유할 수 없는 자원이다 -> 메모리 값으로 관리
@Service
class WebSocketSessionManager(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val redisMessageBroker: RedisMessageBroker,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
) {
    private val logger = LoggerFactory.getLogger(WebSocketSessionManager::class.java)
    private val userSession = ConcurrentHashMap<Long, MutableSet<WebSocketSession>>()

    private val serverRoomsKeyPrefix = "chat:server:rooms"

    @PostConstruct
    fun initialize() {
        redisMessageBroker.setLocalMessageHandler { roomId, msg ->
            sendMessageToLocalRoom(roomId, msg)
        }
    }

    fun addSession(userId: Long, session: WebSocketSession) {
        logger.info("서버에 $userId 세션을 더하는 중")
        userSession.computeIfAbsent(userId) { mutableSetOf() }.add(session)
    }

    fun removeSession(userId: Long, session: WebSocketSession) {
        userSession[userId]?.remove(session)

        if (userSession[userId]?.isEmpty() == true) {
            userSession.remove(userId)

            //열려있는 세션 수 == 연결된 유저 수
            val totalConnectedUsers = userSession.values.sumOf { session ->
                session.count {it.isOpen}
            }

            //세션이 모두 닫혀 있는 경우 == 연결된 유저 수가 0명 == 세션이 불필요하게 형성되어있음
            if (totalConnectedUsers == 0) {
                val serverId = redisMessageBroker.getServerId()
                val serverRoomKey = "${serverRoomsKeyPrefix}$serverId"

                val subscribedRooms = redisTemplate.opsForSet().members(serverRoomKey) ?: emptySet()

                subscribedRooms.forEach { roomIdStr ->
                    val roomId = roomIdStr.toLongOrNull()
                    if (roomId != null) {
                        redisMessageBroker.unsubscribeFromRoom(roomId)
                    }
                }

                redisTemplate.delete(serverRoomKey)
                logger.info("총 $totalConnectedUsers 의 $subscribedRooms 이 제거되었습니다.")
            }
        }
    }

    fun joinRoom(userId: Long, roomId: Long) {
        val serverId = redisMessageBroker.getServerId()
        val serverRoomKey = "${serverRoomsKeyPrefix}$serverId"

        val wasAlreadySubscribed = redisTemplate.opsForSet().isMember(serverRoomKey, roomId.toString()) == true

        if (!wasAlreadySubscribed) {
            redisMessageBroker.subscribeToRoom(roomId)
        }

        redisTemplate.opsForSet().add(serverRoomKey, roomId.toString())


        logger.info("$userId 님이 $serverId 의 $serverRoomKey 로 $roomId 채팅방에 참가하였습니다.")
    }

    fun sendMessageToLocalRoom(roomId: Long, message: ChatMessage, excludeUserId: Long? = null) {
        val json = objectMapper.writeValueAsString(message)

        //채팅방 확인 및 메시지 전송
        userSession.forEach { (userId, session) ->
            val isMember = chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId)

            //같은 채팅방에 속해있는 사용자들에게 메시지 전송
            if (isMember) {
                val closedSessions = mutableSetOf<WebSocketSession>()

                session.forEach { s ->
                    if (s.isOpen) {
                        try {
                            s.sendMessage(TextMessage(json))
                            logger.info("로컬 채팅방 $roomId 에 메시지 전송중")
                        } catch (e: Exception) {
                            logger.error(e.message, e)
                            closedSessions.add(s)
                        }
                    } else {
                        closedSessions.add(s)
                    }
                }

                if (closedSessions.isNotEmpty()) {
                    session.removeAll(closedSessions)
                } else {
                    logger.debug("사용자 $userId 는 채팅방 $roomId 의 멤버가 아닙니다.")
                }
            }
        }
    }

    fun isUserOnlineLocally(userId: Long) : Boolean {
        val sessions = userSession[userId] ?: return false
        val openSession = sessions.filter { it.isOpen }

        if (openSession.size != sessions.size) {
            val closedSessions = sessions.filter { !it.isOpen }

            sessions.removeAll(closedSessions)

            if (sessions.isEmpty()) {
                userSession.remove(userId)
            }
        }

        return openSession.isNotEmpty()
    }
}