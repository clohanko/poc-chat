package com.ycwy.poc_chat.support;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupportThreadRepository extends JpaRepository<SupportThread, String> {
    List<SupportThread> findByCreatedByUserIdOrderByCreatedAtDesc(String userId);

    @Query("""
            select t from SupportThread t
            where exists (select 1 from SupportMessage m where m.threadId = t.id)
              and (t.assignedSupportUserId is null or t.assignedSupportUserId = :userId)
            order by t.createdAt desc
            """)
    List<SupportThread> findVisibleToSupport(@Param("userId") String userId);
}
