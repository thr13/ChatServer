package dto

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "app_users")
@EntityListeners(AuditingEntityListener::class)
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0, //사용자 식별번호

    @Column(unique = true, nullable = false, length = 50)
    @NotBlank
    val username: String, //사용자명(아이디)

    @Column(nullable = false, length = 255)
    val password: String, //비밀번호

    @Column(nullable = false, length = 100)
    val displayName: String, //표시 이름

    @Column(length = 500)
    val profileImageUrl: String? = null, //프로필 이미지 URL

    @Column(length = 50)
    val status: String? = null, //계정 상태

    @Column(nullable = false)
    val isActive: Boolean = true, //계정 활성화 여부

    @Column
    val lastSeenAt: LocalDateTime? = null, //마지막 접속 시각

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(), //계정 생성 시각

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now() //계정 수정 시각
)