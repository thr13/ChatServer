package model

import dto.User
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(
    name = "chat_rooms",
    indexes = [
        Index(name = "idx_chat_room_created_by", columnList = "created_by"),
        Index(name = "idx_chat_room_type", columnList = "type"),
        Index(name = "idx_chat_room_active", columnList = "is_active")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class ChatRoom(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //자동 값 증가
    val id: Long = 0, //채팅방 식별번호

    @Column(nullable = false, length = 100)
    @NotBlank
    val name: String, //채팅방 이름

    @Column(columnDefinition = "TEXT") //DB 에서 TEXT 타입으로 지정
    val description: String? = null, //채팅방 설명

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val type: ChatRoomType = ChatRoomType.GROUP, //채팅방 종류

    @Column(length = 500)
    val imageUrl: String? = null, //썸네일 이미지 URL

    @Column(nullable = false)
    val isActive: Boolean = true, //채팅방 활성화 여부

    @Column(nullable = false)
    val maxMembers: Int = 100, //참여가능한 최대 사용자 수

    @ManyToOne(fetch = FetchType.LAZY) //지연조회, ChatRoom 와 User 는 N:1 관계
    @JoinColumn(name = "created_by", nullable = false)
    val createdBy: User,

    @CreatedDate //insert 시 생성 일자 자동 기록
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(), //채팅방 생성일

    @LastModifiedDate
    @Column(nullable = false) //update 시 마지막 수정 일자 자동 기록
    var updatedAt: LocalDateTime = LocalDateTime.now() //채팅방 수정일
)

enum class ChatRoomType {
    DIRECT,     // 1:1 채팅
    GROUP,      // 그룹 채팅
    CHANNEL     // 채널 (공개)
}