package dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import model.MessageType
import java.time.LocalDateTime

/**
 * Jackson 을 통해 JSON 을 활용하여 타입 정보를 추가함으로써 구분
 * @JsonTypeInfo: JSON 의 직렬화, 역직렬화 과정에서 객체의 타입 정보를 포함하도록 지정 / use = 어떤 아이디를 사용할건지, include = 어디(어느 속성)에 포함되는지, properties: 어떤 이름으로 타입 정보를 넣을지)
 * @JsonSubTypes: 타입에 따라 역직렬화 하는 클래스 구분
 *
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ChatMessage::class, name = "CHAT_MESSAGE"),
    JsonSubTypes.Type(value = ErrorMessage::class, name = "ERROR")
)
sealed class WebSocketMessage {
    abstract val chatRoomId: Long? //채팅방 식별번호
    abstract val timestamp: LocalDateTime //전송 시간
}

// 서버 -> 클라이언트 메시지들
data class ChatMessage(
    val id: Long, //메시지 식별번호
    val content: String, //메시지 내용
    val type: MessageType, //메시지 타입
    val senderId: Long, //전송자 식별번호
    val senderName: String, //전송자 이름
    val sequenceNumber: Long, // 메시지 순서 보장을 위한 시퀀스
    override val chatRoomId: Long, //채팅방 식별번호
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : WebSocketMessage()

data class ErrorMessage(
    val message: String, //오류 내용
    val code: String? = null, //오류 코드
    override val chatRoomId: Long?, //채팅방 식별번호
    override val timestamp: LocalDateTime = LocalDateTime.now()
) : WebSocketMessage()