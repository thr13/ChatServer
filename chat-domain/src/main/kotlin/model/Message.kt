package model

import dto.User
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(
    name = "messages",
    indexes = [
        Index(name = "idx_message_chat_room_id", columnList = "chat_room_id"),
        Index(name = "idx_message_sender_id", columnList = "sender_id"),
        Index(name = "idx_message_created_at", columnList = "created_at"),
        Index(name = "idx_message_room_time", columnList = "chat_room_id,created_at"),
        Index(name = "idx_message_room_sequence", columnList = "chat_room_id,sequence_number")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0, //메시지 식별번호

    @ManyToOne(fetch = FetchType.LAZY) //지연조회, Message 와 ChatRoom 는 N:1 관계
    @JoinColumn(name = "chat_room_id", nullable = false)
    val chatRoom: ChatRoom,

    @ManyToOne(fetch = FetchType.LAZY) //지연조회, Message 와 User 는 N:1 관계
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: User,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val type: MessageType = MessageType.TEXT, //메시지 종류

    @Column(columnDefinition = "TEXT")
    val content: String? = null, //메시지 내용

    @Column(nullable = false)
    val isEdited: Boolean = false, //수정 여부

    @Column(nullable = false)
    val isDeleted: Boolean = false, //삭제 여부

    @Column(nullable = false)
    val sequenceNumber: Long = 0, //메시지 순서 보장을 위한 시퀀스

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(), //메시지 생성 시각

    @Column
    val editedAt: LocalDateTime? = null //메시지 수정 시각
)

enum class MessageType {
    TEXT,
    SYSTEM
}