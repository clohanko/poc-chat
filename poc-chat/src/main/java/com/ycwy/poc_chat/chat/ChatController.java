package com.ycwy.poc_chat.chat;

import com.ycwy.poc_chat.support.SupportMessage;
import com.ycwy.poc_chat.support.SupportMessageService;
import com.ycwy.poc_chat.support.SupportThread;
import com.ycwy.poc_chat.support.SupportThreadRepository;
import com.ycwy.poc_chat.support.dto.ThreadDto;
import com.ycwy.poc_chat.user.User;
import com.ycwy.poc_chat.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.security.Principal;

@Controller
public class ChatController {

    private final SupportMessageService supportMessageService;
    private final SupportThreadRepository supportThreadRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(
            SupportMessageService supportMessageService,
            SupportThreadRepository supportThreadRepository,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.supportMessageService = supportMessageService;
        this.supportThreadRepository = supportThreadRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void send(ChatMessage message, Principal principal) {

        String content = (message.getContent() == null) ? "" : message.getContent().trim();

        // PoC: avoid empty payloads
        if (content.isEmpty()) {
            return;
        }

        String threadId = (message.getThreadId() == null) ? "" : message.getThreadId().trim();
        if (threadId.isEmpty()) {
            return;
        }

        AuthContext authContext = authContext(principal);
        if (authContext == null || authContext.userId.isBlank()) {
            return;
        }

        SupportThread thread = supportThreadRepository.findById(threadId).orElse(null);
        if (thread == null || !canAccessThread(thread, authContext)) {
            return;
        }
        if ("CLOSED".equalsIgnoreCase(thread.getStatus())) {
            return;
        }
        if ("SUPPORT".equals(authContext.role)
                && (thread.getAssignedSupportUserId() == null
                || !thread.getAssignedSupportUserId().equals(authContext.userId))) {
            return;
        }

        boolean hadMessages = supportMessageService.hasMessages(threadId);

        SupportMessage supportMessage = new SupportMessage();
        supportMessage.setContent(content);
        supportMessage.setSentAt(Instant.now());
        supportMessage.setThreadId(threadId);
        supportMessage.setSenderUserId(authContext.userId);

        SupportMessage saved = supportMessageService.save(supportMessage);

        User sender = userRepository.findById(authContext.userId).orElse(null);
        String senderName = sender == null ? null : (sender.getFirstName() + " " + sender.getLastName()).trim();

        ChatMessage payload = new ChatMessage(
                saved.getContent(),
                saved.getSentAt(),
                saved.getThreadId(),
                saved.getSenderUserId(),
                senderName == null || senderName.isBlank() ? (sender == null ? null : sender.getEmail()) : senderName,
                sender == null ? null : sender.getEmail()
        );

        messagingTemplate.convertAndSend("/topic/threads/" + threadId, payload);
        if (!hadMessages) {
            ThreadDto threadDto = toThreadDto(thread);
            messagingTemplate.convertAndSend("/topic/threads", threadDto);
            messagingTemplate.convertAndSend("/topic/users/" + threadDto.createdByUserId() + "/threads", threadDto);
        }
    }

    @MessageMapping("/chat.typing")
    public void typing(TypingEvent event, Principal principal) {
        if (event == null) {
            return;
        }
        String threadId = event.getThreadId() == null ? "" : event.getThreadId().trim();
        if (threadId.isEmpty()) {
            return;
        }

        AuthContext authContext = authContext(principal);
        if (authContext == null || authContext.userId.isBlank()) {
            return;
        }

        SupportThread thread = supportThreadRepository.findById(threadId).orElse(null);
        if (thread == null || !canAccessThread(thread, authContext)) {
            return;
        }
        if ("CLOSED".equalsIgnoreCase(thread.getStatus())) {
            return;
        }
        if ("SUPPORT".equals(authContext.role)
                && (thread.getAssignedSupportUserId() == null
                || !thread.getAssignedSupportUserId().equals(authContext.userId))) {
            return;
        }

        User sender = userRepository.findById(authContext.userId).orElse(null);
        String senderName = sender == null ? null : (sender.getFirstName() + " " + sender.getLastName()).trim();
        TypingEvent payload = new TypingEvent(
                threadId,
                authContext.userId,
                senderName == null || senderName.isBlank() ? (sender == null ? null : sender.getEmail()) : senderName,
                sender == null ? null : sender.getEmail(),
                event.isTyping()
        );
        messagingTemplate.convertAndSend("/topic/threads/" + threadId + "/typing", payload);
    }

    private boolean canAccessThread(SupportThread thread, AuthContext authContext) {
        if ("SUPPORT".equals(authContext.role)) {
            String assigned = thread.getAssignedSupportUserId();
            return assigned == null || assigned.equals(authContext.userId);
        }
        return "CLIENT".equals(authContext.role) && thread.getCreatedByUserId().equals(authContext.userId);
    }

    private AuthContext authContext(Principal principal) {
        if (!(principal instanceof Authentication authentication)) {
            return null;
        }
        Object details = authentication.getDetails();
        if (!(details instanceof Claims claims)) {
            return null;
        }
        String userId = claims.get("uid", String.class);
        String role = claims.get("role", String.class);
        return userId == null || role == null ? null : new AuthContext(userId, role);
    }

    private record AuthContext(String userId, String role) {
    }

    private ThreadDto toThreadDto(SupportThread thread) {
        User user = userRepository.findById(thread.getCreatedByUserId()).orElse(null);
        User assignedSupport = thread.getAssignedSupportUserId() == null
                ? null
                : userRepository.findById(thread.getAssignedSupportUserId()).orElse(null);
        return new ThreadDto(
                thread.getId(),
                thread.getSubject(),
                thread.getStatus(),
                thread.getCreatedAt(),
                thread.getCreatedByUserId(),
                displayName(user),
                user == null ? null : user.getEmail(),
                thread.getReservationId(),
                thread.getAssignedSupportUserId(),
                displayName(assignedSupport),
                assignedSupport == null ? null : assignedSupport.getEmail()
        );
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName();
        String last = user.getLastName();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? user.getEmail() : full;
    }
}
