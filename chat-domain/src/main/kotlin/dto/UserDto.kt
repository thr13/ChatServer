package dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

//사용자 DTO
data class UserDto(
    val id: Long, //사용자 식별번호
    val username: String, //사용자명(아이디)
    val displayName: String, //표시 이름
    val profileImageUrl: String?, //프로필 이미지 URL
    val status: String?, //사용자 상태
    val isActive: Boolean, //활성화 여부
    val lastSeenAt: LocalDateTime?, //최근 접속 시각
    val createdAt: LocalDateTime //계정 생성 시각
)

//사용자 생성 요청
data class CreateUserRequest(
    @field:NotBlank(message = "사용자명은 필수입니다")
    @field:Size(min = 3, max = 20, message = "사용자명은 3-20자 사이여야 합니다")
    val username: String, //사용자명(아이디)

    @field:NotBlank(message = "비밀번호는 필수입니다")
    @field:Size(min = 3, message = "비밀번호는 최소 3자 이상이어야 합니다")
    val password: String, //비밀번호

    @field:NotBlank(message = "표시 이름은 필수입니다")
    @field:Size(min = 1, max = 50, message = "표시 이름은 1-50자 사이여야 합니다")
    val displayName: String //표시 이름
)

//로그인 요청
data class LoginRequest(
    @field:NotBlank(message = "사용자명은 필수입니다")
    val username: String, //사용자명(아이디)

    @field:NotBlank(message = "비밀번호는 필수입니다")
    val password: String //비밀번호
)