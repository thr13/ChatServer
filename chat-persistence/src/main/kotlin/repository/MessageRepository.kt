package repository

import model.Message
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface MessageRepository : JpaRepository<Message, Long> {

    @Query("""
        SELECT m FROM Message m
        JOIN FETCH m.sender s
        JOIN FETCH m.chatRoom cr
        WHERE m.chatRoom.id = :chatRoomId AND m.isDeleted = false
        ORDER BY m.sequenceNumber DESC, m.createdAt DESC
    """)
    fun findByChatRoomId(chatRoomId: Long, pageable: Pageable): Page<Message>

    @Query("""
        SELECT m FROM Message m
        JOIN FETCH m.sender s
        JOIN FETCH m.chatRoom cr
        WHERE m.chatRoom.id = :chatRoomId
        AND m.isDeleted = false
        AND m.id < :cursor
        ORDER BY m.sequenceNumber DESC, m.createdAt DESC
    """)
    fun findMessagesBefore(chatRoomId: Long, cursor: Long, pageable: Pageable): List<Message>

    @Query("""
        SELECT m FROM Message m
        JOIN FETCH m.sender s
        JOIN FETCH m.chatRoom cr
        WHERE m.chatRoom.id = :chatRoomId
        AND m.isDeleted = false
        AND m.id > :cursor
        ORDER BY m.sequenceNumber ASC, m.createdAt ASC
    """)
    fun findLatestMessages(chatRoomId: Long, pageable: Pageable): List<Message>

    @Query(value = """
        SELECT * FROM messages m
        WHERE m.chat_room_id = :chatRoomId AND m.is_deleted = false
        ORDER BY m.sequence_number DESC, m.created_at DESC
        LIMIT 1
    """, nativeQuery = true)
    fun findLatestMessage(chatRoomId: Long): Message?
}