package service

import dto.*
import model.*
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
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

    private val logger = LoggerFactory.getLogger(ChatServiceImpl::class.java)

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

    //채팅방 탈퇴 => 채팅방의 상태 조율 => 논리적인 캐시 삭제 구현
    @Caching(evict = [
        CacheEvict(value = ["chatRoomMembers"], key = "#roomId"),
    CacheEvict(value = ["chatRooms"], key = "#roomId")
    ])
    override fun leaveChatRoom(roomId: Long, userId: Long) {
        chatRoomMemberRepository.leaveChatRoom(roomId, userId)
    }

    @Cacheable(value = ["chatRoomMembers"], key = "#roomId")
    override fun getChatRoomMembers(roomId: Long): List<ChatRoomMemberDto> {
        return chatRoomMemberRepository.findByChatRoomIdAndIsActiveTrue(roomId)
            .map { memberToDto(it) }
    }

    override fun sendMessage(
        request: SendMessageRequest,
        senderId: Long
    ): MessageDto {

        val chatRoom = chatRoomRepository.findById(request.chatRoomId)
            .orElseThrow { IllegalArgumentException("채팅방을 찾을 수 없습니다: ${request.chatRoomId}") }

        val sender = userRepository.findById(senderId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다: $senderId") }

        chatRoomMemberRepository.findByChatRoomIdAndUserIdAndUserIdAndIsActiveTrue(request.chatRoomId, senderId)
            .orElseThrow { IllegalArgumentException("채팅방에 참여하지 않은 사용자입니다.") }

        //메시지 순서 생성 => 채팅방별로 고유 시퀀스 부여
        val sequenceNumber = messageSequenceService.getNextSequence(request.chatRoomId)
        val message = Message(
            content = request.content,
            type = request.type ?: MessageType.TEXT,
            chatRoom = chatRoom,
            sender = sender,
            sequenceNumber = sequenceNumber
        )
        //들어온 메시지 저장 => 메시지 저장이 웹소켓이므로 메시지를 전송한 세션이 로컬 세션이 된다
        val savedMessage = messageRepository.save(message)
        val chatMessage = ChatMessage(
            id = savedMessage.id,
            content = savedMessage.content ?: "",
            type = savedMessage.type,
            chatRoomId = savedMessage.chatRoom.id,
            senderId = savedMessage.sender.id,
            senderName = savedMessage.sender.displayName,
            sequenceNumber = savedMessage.sequenceNumber,
            timestamp = savedMessage.createdAt
        )
        //로컬 세션에 메시지를 즉시 전송 => 클라이언트 실시간 응답성 보장
        webSocketSessionManager.sendMessageToLocalRoom(request.chatRoomId, chatMessage)

        //현재 이 서버와 연결된 다른 서버들에게도 메시지 전달 필요 => 서버 인스턴스에 브로드캐스트 (자신 제외)
        try {
            redisMessageBroker.broadcastToRoom(
                roomId = request.chatRoomId,
                message = chatMessage,
                excludeServerId = redisMessageBroker.getServerId()
            )
        } catch (e: Exception) {
            logger.error("Failed to broadcast message via Redis: ${e.message}", e)
        }

        return messageToDto(savedMessage)
    }

    //커서 기반 페이징 x => Pageable 의 첫번째, 두번째 페이지가 아닌 추가적으로 가져오는 데이터의 limit 이 동적인 값이므로 캐싱 처리 Xx
    override fun getMessages(
        roomId: Long,
        userId: Long,
        pageable: Pageable
    ): Page<MessageDto> {
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId)) {
            throw IllegalArgumentException("채팅방 멤버가 아닙니다.")
        }

        return messageRepository.findByChatRoomId(roomId, pageable)
            .map { messageToDto(it) }
    }

    /*
        커서 기반 페이징
        => 데이터를 가져오는 행위에 대해 쿼리의 WHERE 조건문에 값을 추가하면서 동작하는 것을 의미함
        => [MySQL 튜닝 기법]
            SELECT *
            FROM chat_room_member
            WHERE chat_room_id = :chatRoomId AND is_active = true
            ORDER BY id
            LIMIT 10 OFFSET 10;

        위 쿼리는 정렬을 진행하고 그 이후에 데이터를 쪼개서 가져옴
        => 정렬 자체는 한번 전체적으로 스캔하며 동작함
        => 데이터가 많을 경우 인덱스를 탄다고 해도 느려질 것 이다

            SELECT *
            FROM chat_room_member
            WHERE chat_room_id = :chatRoomId AND is_active = true AND id > :cursor
            ORDER BY id ASC
            LIMIT 10;

        => 전체적으로 스캔을 하지 않고 정렬해야 데이터의 개수를 줄일 수 있다
        => 기본적으로 WHERE 조건을 통해 탐색해야 하는 데이터의 개수를 줄인다
        => 즉, 매칭되는 데이터가 많을수록 MySQL 엔진이 더 적은 데이터를 다룰 수 있다
        => 이러한 관점에서 나온게 커서베이스의 페이징이다
    */
    override fun getMessagesByCursor(request: MessagePageRequest, userId: Long): MessagePageResponse {

        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndIsActiveTrue(request.chatRoomId, userId)) {
            throw IllegalArgumentException("채팅방 멤버가 아닙니다")
        }

        val pageable = PageRequest.of(0, request.limit)

        // 로컬 변수로 복사하여 스마트 캐스트 가능하게 함
        val cursor = request.cursor

        val messages = when {
            // 커서가 없으면 최신 메시지부터 클라이언트에게 전달
            cursor == null -> {
                messageRepository.findLatestMessages(request.chatRoomId, pageable)
            }
            // 커서 이전 메시지들 (과거 방향)
            request.direction == MessageDirection.BEFORE -> {
                messageRepository.findMessagesBefore(request.chatRoomId, cursor, pageable)
            }
            // 커서 이후 메시지들 (최신 방향)
            else -> {
                messageRepository.findMessagesAfter(request.chatRoomId, cursor, pageable)
                    .reversed()
            }
        }

        val messageDtos = messages.map { messageToDto(it) }

        // 다음,이전 커서
        val nextCursor = if (messageDtos.isNotEmpty()) messageDtos.last().id else null
        val prevCursor = if (messageDtos.isNotEmpty()) messageDtos.first().id else null

        // 추가 데이터 존재 여부 확인
        val hasNext = messages.size == request.limit
        val hasPrev = cursor != null

        return MessagePageResponse(
            messages = messageDtos,
            nextCursor = nextCursor,
            prevCursor = prevCursor,
            hasNext = hasNext,
            hasPrev = hasPrev
        )
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