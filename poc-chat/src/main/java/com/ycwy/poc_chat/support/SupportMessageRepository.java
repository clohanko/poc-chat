package com.ycwy.poc_chat.support;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, String> {
    List<SupportMessage> findByThreadIdOrderBySentAtAsc(String threadId);
    boolean existsByThreadId(String threadId);
}
