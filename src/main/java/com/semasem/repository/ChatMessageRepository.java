package com.semasem.repository;

import com.semasem.repository.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    Page<ChatMessage> findByRoomUuidOrderByTimestampDesc(UUID roomUuid, Pageable pageable);

    List<ChatMessage> findTop50ByRoomUuidOrderByTimestampAsc(UUID roomUuid);

    Optional<ChatMessage> findByUuidAndRoomUuid(UUID messageUuid, UUID roomUuid);

    @Query("SELECT COUNT(cm) FROM ChatMessage cm WHERE cm.room.uuid = :roomUuid")
    long countByRoomUuid(@Param("roomUuid") UUID roomUuid);

    List<ChatMessage> findByRoomUuidAndTimestampAfterOrderByTimestampAsc(
            UUID roomUuid, Instant afterTimestamp);
}
