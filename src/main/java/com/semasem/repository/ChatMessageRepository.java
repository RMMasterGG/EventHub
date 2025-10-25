package com.semasem.repository;

import com.semasem.repository.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("SELECT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.user " +
            "LEFT JOIN FETCH cm.room " +
            "WHERE cm.room.uuid = :roomId " +
            "ORDER BY cm.timestamp DESC LIMIT :limit")
    List<ChatMessage> findByRoomUuidWithUserAndRoom(@Param("roomId") UUID roomId, @Param("limit") int limit);

    @Query(value = "SELECT cm FROM ChatMessage cm " +
            "LEFT JOIN FETCH cm.user " +
            "LEFT JOIN FETCH cm.room " +
            "WHERE cm.room.uuid = :roomId " +
            "ORDER BY cm.timestamp DESC LIMIT :limit",
            nativeQuery = false)
    List<ChatMessage> findByRoomUuidWithUserAndRoomLimited(@Param("roomId") UUID roomId, @Param("limit") int limit);

    // Старые методы (можно удалить или оставить)
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.room.uuid = :roomId ORDER BY cm.timestamp DESC")
    List<ChatMessage> findByRoomUuidOrderByTimestampDesc(@Param("roomId") UUID roomId);

    @Query("SELECT cm FROM ChatMessage cm WHERE cm.room.uuid = :roomId ORDER BY cm.timestamp DESC LIMIT :limit")
    List<ChatMessage> findByRoomUuidOrderByTimestampDesc(@Param("roomId") UUID roomId, @Param("limit") int limit);
}