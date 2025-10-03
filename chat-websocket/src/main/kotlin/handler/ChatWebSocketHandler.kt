package handler

import com.fasterxml.jackson.databind.ObjectMapper
import dto.ErrorMessage
import dto.SendMessageRequest
import model.MessageType
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.*
import service.ChatService
import service.WebSocketSessionManager
import java.io.IOException

@Component
class ChatWebSocketHandler(
    private val sessionManager: WebSocketSessionManager,
//    private val messageService: WebSocketMessageSEr,
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper
) : WebSocketHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = getUserIdFromSession(session)

        if (userId != null) {
            //세션 정보
            sessionManager.addSession(userId, session)
            logger.info("$userId 를 위해 세션 $session 이 설립되었습니다")

            try {
                //로그용 채팅방
                loadUserChatRooms(userId)
            } catch (e: Exception) {
                logger.error("사용자 참여 채팅방 로딩 중 오류", e)
            }
        }
    }

    //들어오는 메시지가 호출될때 실행되는 함수
    override fun handleMessage(
        session: WebSocketSession,
        message: WebSocketMessage<*>
    ) {
        val userId = getUserIdFromSession(session) ?: return

        try {
            when(message) {
                is TextMessage-> {
                    handleTextMessage(session, userId, message.payload)
                }
                else -> {
                    logger.warn("지원되지 않는 메시지 타입: ${message.javaClass.name}")
                }
            }
        } catch (e : Exception) {
            logger.warn("메시지 처리 중 오류 발생", e)
            //메시지 처리 중 발생한 오류 메시지를 클라이언트에게 넘겨줌
            sendErrorMessage(session, "메시지 처리 에러")
        }
    }

    //웹소켓 연결에 문제가 있는 때, 트리거가 자동으로 동작
    override fun handleTransportError(
        session: WebSocketSession,
        exception: Throwable
    ) {
        val userId = getUserIdFromSession(session)

        //EOFException: 클라이언트 연결 해제(정상적인 상황) => 로그레벨을 따로 두기 위해 구현함
        if (exception is java.io.EOFException) {
            logger.debug("WebSocket connection closed by client for user: $userId")
        } else {
            logger.error("WebSocket transport error for user: $userId", exception)
        }

        if (userId != null) {
            sessionManager.removeSession(userId, session)
        }
    }

    //커넥션 종료시 세션 삭제
    override fun afterConnectionClosed(
        session: WebSocketSession,
        closeStatus: CloseStatus
    ) {
        val userId = getUserIdFromSession(session)

        if (userId != null) {
            sessionManager.removeSession(userId, session)
            logger.info("사용자 $userId 이 세션에서 제거되었습니다")
        }
    }

    //큰 메시지가 들어올 경우 데이터를 쪼개서 받을지 정책 결정
    override fun supportsPartialMessages(): Boolean = false

    //세션에서 userId 를 가져옴
    private fun getUserIdFromSession(session: WebSocketSession): Long? {
        return session.attributes["userId"] as? Long
    }

    //사용자가 참여중인 채팅방 목록 조회
    private fun loadUserChatRooms(userId: Long) {
        try {
            val chatRooms = chatService.getChatRooms(userId, PageRequest.of(0, 100))

            chatRooms.content.forEach { room ->
                sessionManager.joinRoom(userId, room.id)
            }

            logger.info("사용자가 참여한 채팅방 목록 ${chatRooms.content.size} 불러오는중")
        } catch (e: Exception) {
            logger.error("사용자가 참여한 채팅방 목록 불러오기 실패: $userId", e)
        }
    }

    private fun sendErrorMessage(session: WebSocketSession, errorMessage: String, errorCode: String? = null) {
        try {
            val error = ErrorMessage(
                chatRoomId = null,
                message = errorMessage,
                code = errorCode
            )
            val json = objectMapper.writeValueAsString(error)
            session.sendMessage(TextMessage(json))
        } catch (e: IOException) {
            logger.error("오류메시지 전송 실패", e)
        }
    }

    private fun extractMessageType(payload: String): String? {
        return try {
            objectMapper.readTree(payload).get("type")?.asText()
        } catch (e: Exception) {
            null
        }
    }

    private fun handleTextMessage(session: WebSocketSession, userId: Long, payload: String) {
        try {
            //들어온 메시지 타입 => 웹소켓 통신시 페이로드 값을 담아서 보냄 => 페이로드에 메시지의 타입, 키 JSON Object 확인
            val messageType = extractMessageType(payload)

            when (messageType) {
                "SEND_MESSAGE" -> {
                    val jsonNode = objectMapper.readTree(payload)

                    val chatRoomId = jsonNode.get("chatRoomId")?.asLong() ?: throw IllegalArgumentException("chatRoomId is required")
                    val messageTypeText = jsonNode.get("messageType")?.asText() ?: throw IllegalArgumentException("messageType is required")
                    val content = jsonNode.get("content")?.asText()

                    val sendMessageRequest = SendMessageRequest(
                        chatRoomId = chatRoomId,
                        type = MessageType.valueOf(messageTypeText),
                        content = content
                    )

                    chatService.sendMessage(sendMessageRequest, userId)
                }

                else -> {
                    logger.warn("알 수 없는 메시지 타입: $messageType")
                    sendErrorMessage(session, "알 수 없는 메시지 타입입니다: $messageType", "UNKNOWN_MESSAGE_TYPE")
                }
            }
        } catch (e: Exception) {
            logger.error("사용자 $userId 가 보낸 WebSocket 메시지를 구문 분석하는 중 오류 발생: ${e.message}", e)
            sendErrorMessage(session, "메시지 형식이 올바르지 않습니다.", "INVALID_MESSAGE_FORMAT")
        }
    }

}