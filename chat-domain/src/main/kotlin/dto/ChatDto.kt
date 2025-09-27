package dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import model.ChatRoomType
import model.MemberRole
import model.MessageType
import java.time.LocalDateTime

//채팅방 DTO
data class ChatRoomDto(
    val id: Long, //채팅방 식별번호
    val name: String, //채팅방 이름
    val description: String, //채팅방 설명
    val type: ChatRoomType, //채팅방 종류
    val imageUrl: String?, //썸네일 이미지 URL
    val isActive: Boolean, //활성화 여부
    val maxMembers: Int, //참여가능한 최대 사용자 수
    val memberCount: Int, //사용자 수
)

//채팅방 생성 요청
data class CreatedChatRoomRequest(
    @field:NotBlank(message = "채팅방 이름은 필수입니다")
    @field:Size(min = 1, max = 100, message = "채팅방 이름은 1-100자 사이여야 합니다")
    val name: String, //채팅방 이름
    
    val description: String?, //채팅방 설명(Nullable)
    val maxMembers: Int = 100, //참여가능한 최대 사용자 수

)

//메시지 DTO
data class MessageDto(
    val id: Long, //메시지 식별번호
    val chatRoomId: Long, //채팅방 식별번호
    val sender: UserDto, //전송 데이터(사용자 DTO)
    val type: MessageType, //메시지 타입
    val content: String?,//메시지 내용(Nullable)
    val isEdited: Boolean, //수정 여부
    val isDeleted: Boolean, //삭제 여부
    val createdAt: LocalDateTime, //생성 시각
    val editedAt: LocalDateTime?, //수정 시각
    val sequenceNumber: Long = 0 //메시지 순서 보장을 위한 시퀀스

)

//메시지 전송 요청
data class SendMessageRequest(
    @field:NotNull(message = "채팅방 ID는 필수입니다")
    val chatRoomId: Long, //메시지 식별번호

    @field:NotNull(message = "메시지 타입은 필수입니다")
    val type: MessageType, //메시지 타입

    val content: String? //메시지 내용

)

//메시지 페이지네이션 DTO
data class MessagePageRequest(
    val chatRoomId: Long, //메시지 식별번호
    val cursor: Long? = null, //마지막 메시지 ID (없으면 최신부터)
    val limit: Int = 50, //최대 페이지 수
    val direction: MessageDirection = MessageDirection.BEFORE //커서 기준 이전/이후
)

//메시지 페이징 요청
data class MessagePageResponse(
    val messages: List<MessageDto>, //메시지 목록
    val nextCursor: Long?, //다음 페이지를 위한 커서
    val prevCursor: Long?, //이전 페이지를 위한 커서
    val hasNext: Boolean, //다음 페이지 존재 여부
    val hasPrev: Boolean //이전 페이지 존재 여부
)

enum class MessageDirection {
    BEFORE, //페이지 커서 이전 메시지들(과거)
    AFTER //페이지 커서 이후 메시지들(최신)
}

//채팅방 사용자 DTO
data class ChatRoomMemberDto(
    val id: Long, //채팅방 식별번호
    val user: UserDto, //사용자 DTO
    val role: MemberRole, //사용자 등급
    val isActive: Boolean, //채팅방 활성화 여부
    val lastReadMessageId: Long?, //가장 최신 메시지 식별번호
    val joinedAt: LocalDateTime, //채팅방 참가 시각
    val leftAt: LocalDateTime? //채팅방 탈퇴 시각
)
