package com.ycwy.poc_chat.support;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportMessageService {

    private final SupportMessageRepository supportMessageRepository;

    public SupportMessageService(SupportMessageRepository supportMessageRepository) {
        this.supportMessageRepository = supportMessageRepository;
    }

    @Transactional
    public SupportMessage save(SupportMessage message) {
        return supportMessageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public boolean hasMessages(String threadId) {
        return supportMessageRepository.existsByThreadId(threadId);
    }
}
