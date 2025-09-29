package repository

import model.ChatRoom
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatRoomRepository : CrudRepository<ChatRoom, Long> {

    @Query("""
        SELECT DISTINCT cr FROM ChatRoom cr
        JOIN ChatRoomMember crm ON cr.id = crm.chatRoom.id
        WHERE crm.user.id = :userId AND crm.isActive = true AND cr.isActive = true
        ORDER BY cr.updatedAt DESC
    """)
    fun findUserChatRooms(userId: Long, pageable: Pageable): Page<ChatRoom>

    fun findByIsActiveTrueOrderByCreatedAtDesc(): List<ChatRoom>

    fun findByNameContainingIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc(name: String): List<ChatRoom>

}