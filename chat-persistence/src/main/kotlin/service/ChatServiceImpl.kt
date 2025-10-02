package service

import dto.*
import model.*
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import redis.RedisMessageBroker
import repository.*


/*
    @CacheEvit: 캐시 삭제, 속성 allEntries 전체 캐시 삭제 기능, 속성 beforeInvocation 메서드 실행 전에 캐시 삭제
    @Cacheable: 정해진 순서대로 캐시 데이터를 제공 및 관리 -> 정해진 캐시키(캐시히트)가 존재한다면 캐시 데이터를 반환하고 존재하지 않다면 붙어있는 펑션 내부 로직 진행
    @Caching: @CacheEvit, @Cacheable 와 같은 어노테이션을 동적으로 적용할 수 있게하거나 @Caching 을 여러 개 적용할 수 있다
 */

@Service
@Transactional
class ChatServiceImpl(
    private val chatRoomRepository: ChatRoomRepository,
    private val messageRepository: MessageRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val userRepository: UserRepository,
    private val redisMessageBroker: RedisMessageBroker,
    private val messageSequenceService: MessageSequenceService,
    private val webSocketSessionManager: WebSocketSessionManager
) : ChatService {

    //채팅방을 새롭게 생성시 기존에 남아있던 캐시 데이터 삭제 필요 => @CacheEvit 사용
    @CacheEvict(value = ["chatRooms"], allEntries = true)
    override fun createChatRoom(
        //어떤 사용자가 요청을 하는지에 대한 값이 요청으로 들어옴
        request: CreatedChatRoomRequest,
        createdBy: Long
    ): ChatRoomDto {
        val creator = userRepository.findById(createdBy)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다: $createdBy") }

        val chatRoom = ChatRoom(
            name = request.name,
            description = request.description,
            type = request.type,
            imageUrl = request.imageUrl,
            maxMembers = request.maxMembers,
            createdBy = creator
        )

        val savedRoom = chatRoomRepository.save(chatRoom)
        val ownerMember = ChatRoomMember(
            chatRoom = savedRoom,
            user = creator,
            role = MemberRole.OWNER
        )
        chatRoomMemberRepository.save(ownerMember)

        //생성자 세션 갱신
        if (webSocketSessionManager.isUserOnlineLocally(creator.id)) {
            webSocketSessionManager.joinRoom(creator.id, savedRoom.id)
        }

        return chatRoomToDto(savedRoom)
    }

    @Cacheable(value = ["chatRooms"], key = "#roomId")
    override fun getChatRoom(roomId: Long): ChatRoomDto {
        val chatRoom = chatRoomRepository.findById(roomId)
            .orElseThrow { IllegalArgumentException("채팅방을 찾을 수 없습니다: $roomId") }

        return chatRoomToDto(chatRoom)
    }

    override fun getChatRooms(
        userId: Long,
        pageable: Pageable
    ): Page<ChatRoomDto> {
        return chatRoomRepository.findUserChatRooms(userId, pageable)
            .map { chatRoomToDto(it) }
    }

    //검색은 동적이고 정해져있는 쿼리가 아니므로 들어오는 파라미터가 실시간으로 자주 바뀌기 때문에 캐싱 처리 X
    override fun searchChatRooms(
        query: String,
        userId: Long
    ): List<ChatRoomDto> {
        val chatRooms = if (query.isBlank()) {
            chatRoomRepository.findByIsActiveTrueOrderByCreatedAtDesc()
        } else {
            chatRoomRepository.findByNameContainingIgnoreCaseAndIsActiveTrueOrderByCreatedAtDesc(query)
        }

        return chatRooms.map { chatRoomToDto(it) }
    }

    //DB 에서 채팅방 멤버의 최신 데이터를 조회하기 위해서는, 조회하기 전 기존 캐시를 무효화 시켜야 한다
    @Caching(evict = [
        CacheEvict(value = ["chatRoomMembers"], key = "#roomId"),
        CacheEvict(value = ["chatRooms"], key = "#roomId"),
    ])
    override fun joinChatRoom(roomId: Long, userId: Long) {
        //채팅방 확인
        val chatRoom = chatRoomRepository.findById(roomId)
            .orElseThrow { IllegalArgumentException("채팅방을 찾을 수 없습니다: $roomId") }

        //사용자 확인
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다: $userId") }

        //이미 참여중인지 확인
        if (chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId)) {
            throw IllegalStateException("이미 참여한 채팅방입니다.")
        }

        val currentMemberCount = chatRoomMemberRepository.countActiveMembersInRoom(roomId)
        if (currentMemberCount >= chatRoom.maxMembers) {
            throw IllegalStateException("채팅방이 가득 찼습니다")
        }

        val member = ChatRoomMember(
            chatRoom = chatRoom,
            user = user,
            role = MemberRole.MEMBER
        )
        chatRoomMemberRepository.save(member)

        if (webSocketSessionManager.isUserOnlineLocally(userId)) {
            webSocketSessionManager.joinRoom(userId, roomId)
        }
    }

    override fun leaveChatRoom(roomId: Long, userId: Long) {
        TODO("Not yet implemented")
    }

    override fun getChatRoomMembers(roomId: Long): List<ChatRoomMemberDto> {
        TODO("Not yet implemented")
    }

    override fun sendMessage(request: SendMessageRequest, senderId: Long): MessageDto {
        TODO("Not yet implemented")
    }

    override fun getMessages(roomId: Long, userId: Long, pageable: Pageable): Page<MessageDto> {
        TODO("Not yet implemented")
    }

    override fun getMessagesByCursor(request: MessagePageRequest, userId: Long): MessagePageResponse {
        TODO("Not yet implemented")
    }

    @Cacheable(value = ["chatRooms"], key = "#chatRoom.id")
    private fun chatRoomToDto(chatRoom: ChatRoom): ChatRoomDto {
        //캐싱 처리
        val memberCount = chatRoomMemberRepository.countActiveMembersInRoom(chatRoom.id).toInt()
        val lastMessage = messageRepository.findLatestMessage(chatRoom.id)?.let { messageToDto(it) }

        return ChatRoomDto(
            id = chatRoom.id,
            name = chatRoom.name,
            description = chatRoom.description,
            type = chatRoom.type,
            imageUrl = chatRoom.imageUrl,
            isActive = chatRoom.isActive,
            maxMembers = chatRoom.maxMembers,
            memberCount = memberCount,
            createdBy = userToDto(chatRoom.createdBy),
            createdAt = chatRoom.createdAt,
            lastMessage = lastMessage
        )
    }

    private fun messageToDto(message: Message): MessageDto {
        return MessageDto(
            id = message.id,
            chatRoomId = message.chatRoom.id,
            sender = userToDto(message.sender),
            type = message.type,
            content = message.content,
            isEdited = message.isEdited,
            isDeleted = message.isDeleted,
            createdAt = message.createdAt,
            editedAt = message.editedAt,
            sequenceNumber = message.sequenceNumber
        )
    }

    private fun memberToDto(member: ChatRoomMember): ChatRoomMemberDto {
        return ChatRoomMemberDto(
            id = member.id,
            user = userToDto(member.user),
            role = member.role,
            isActive = member.isActive,
            lastReadMessageId = member.lastReadMessageId,
            joinedAt = member.joinedAt,
            leftAt = member.leftAt
        )
    }

    //user 값은 변경이 잘 일어나지 않음 => 반환값인 UserDto 에 @Cacheable 을 적용 => 캐시 데이터로 관리
    @Cacheable(value = ["users"], key = "#user.id")
    private fun userToDto(user: User): UserDto {
        return UserDto(
            id = user.id,
            username = user.username,
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl,
            status = user.status,
            isActive = user.isActive,
            lastSeenAt = user.lastSeenAt,
            createdAt = user.createdAt
        )
    }
}