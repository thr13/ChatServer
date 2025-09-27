package model

import dto.User
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(
    name = "chat_room_members",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["chat_room_id", "user_id"])
    ],
    indexes = [
        Index(name = "idx_chat_room_member_user_id", columnList = "user_id"),
        Index(name = "idx_chat_room_member_chat_room_id", columnList = "chat_room_id"),
        Index(name = "idx_chat_room_member_active", columnList = "is_active"),
        Index(name = "idx_chat_room_member_role", columnList = "role")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class ChatRoomMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0, //채팅방내 사용자 식별번호

    @ManyToOne(fetch = FetchType.LAZY) //지연조회, ChatRoomMember 와 ChatRoom 는 N:1 관계
    @JoinColumn(name = "chat_room_id", nullable = false)
    val chatRoom: ChatRoom,

    @ManyToOne(fetch = FetchType.LAZY) //지연조회, ChatRoomMember 와 User 는 N:1 관계
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING) //Enum 타입을 DB 에 문자열로 저장
    val role: MemberRole = MemberRole.MEMBER, // 채팅방내 사용자 종류

    @Column(nullable = false)
    val isActive: Boolean = true, //채팅방내 사용자 활성화 여부

    @Column
    val lastReadMessageId: Long? = null, //마지막으로 보낸 메시지 식별번호(최신 메시지)

    @Column(nullable = false)
    val joinedAt: LocalDateTime = LocalDateTime.now(), //채팅방 참가 시각

    @Column
    val leftAt: LocalDateTime? = null, //채팅방 탈퇴 시각

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now() //채팅방 사용자 객체 생성 시각
)

enum class MemberRole {
    OWNER,      // 방장
    ADMIN,      // 관리자
    MEMBER      // 일반 멤버
}