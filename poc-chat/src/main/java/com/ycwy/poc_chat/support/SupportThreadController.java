package com.ycwy.poc_chat.support;

import com.ycwy.poc_chat.security.SecurityUtils;
import com.ycwy.poc_chat.chat.ChatMessage;
import com.ycwy.poc_chat.support.dto.MessageDto;
import com.ycwy.poc_chat.support.dto.ThreadDto;
import com.ycwy.poc_chat.user.User;
import com.ycwy.poc_chat.user.UserRepository;
import com.ycwy.poc_chat.reservation.Reservation;
import com.ycwy.poc_chat.reservation.ReservationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/threads")
public class SupportThreadController {

    private final SupportThreadRepository threadRepository;
    private final SupportMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public SupportThreadController(
            SupportThreadRepository threadRepository,
            SupportMessageRepository messageRepository,
            UserRepository userRepository,
            ReservationRepository reservationRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public List<ThreadDto> listThreads() {
        String role = SecurityUtils.currentRole();
        String userId = SecurityUtils.currentUserId();
        List<SupportThread> threads;
        if ("CLIENT".equals(role)) {
            threads = threadRepository.findByCreatedByUserIdOrderByCreatedAtDesc(userId);
        } else {
            threads = threadRepository.findVisibleToSupport(userId);
        }

        Set<String> userIds = threads.stream()
                .flatMap(thread -> {
                    if (thread.getAssignedSupportUserId() == null) {
                        return java.util.stream.Stream.of(thread.getCreatedByUserId());
                    }
                    return java.util.stream.Stream.of(thread.getCreatedByUserId(), thread.getAssignedSupportUserId());
                })
                .collect(Collectors.toSet());
        Map<String, User> users = loadUsers(userIds);

        return threads.stream()
                .map(thread -> {
                    User user = users.get(thread.getCreatedByUserId());
                    User assignedSupport = thread.getAssignedSupportUserId() == null
                            ? null
                            : users.get(thread.getAssignedSupportUserId());
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
                })
                .toList();
    }

    @PostMapping
    public ThreadDto createThread(@RequestBody CreateThreadRequest request) {
        String role = SecurityUtils.currentRole();
        if (!"CLIENT".equals(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Not allowed");
        }
        String userId = SecurityUtils.currentUserId();
        String subject = request == null ? null : request.subject();
        if (subject == null || subject.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Subject is required");
        }
        String reservationId = request == null ? null : request.reservationId();
        reservationId = reservationId == null ? null : reservationId.trim();
        if (reservationId != null && reservationId.isEmpty()) {
            reservationId = null;
        }
        if (reservationId != null) {
            Reservation reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Reservation not found"));
            if (!reservation.getUserId().equals(userId)) {
                throw new ResponseStatusException(FORBIDDEN, "Not allowed");
            }
        }

        SupportThread thread = new SupportThread();
        thread.setId(UUID.randomUUID().toString());
        thread.setCreatedAt(Instant.now());
        thread.setSubject(subject.trim());
        thread.setStatus("OPEN");
        thread.setCreatedByUserId(userId);
        thread.setReservationId(reservationId);
        thread.setAssignedSupportUserId(null);
        SupportThread saved = threadRepository.save(thread);

        User user = userRepository.findById(userId).orElse(null);
        ThreadDto dto = new ThreadDto(
                saved.getId(),
                saved.getSubject(),
                saved.getStatus(),
                saved.getCreatedAt(),
                saved.getCreatedByUserId(),
                displayName(user),
                user == null ? null : user.getEmail(),
                saved.getReservationId(),
                saved.getAssignedSupportUserId(),
                null,
                null
        );
        publishThreadUpdate(dto, false);
        return dto;
    }

    @PostMapping("/{threadId}/close")
    public ThreadDto closeThread(@PathVariable String threadId) {
        String role = SecurityUtils.currentRole();
        if (!"SUPPORT".equals(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Not allowed");
        }
        String userId = SecurityUtils.currentUserId();

        SupportThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Thread not found"));
        String assigned = thread.getAssignedSupportUserId();
        if (assigned == null || !assigned.equals(userId)) {
            throw new ResponseStatusException(FORBIDDEN, "Not allowed");
        }
        if ("CLOSED".equalsIgnoreCase(thread.getStatus())) {
            return toDto(thread);
        }
        thread.setStatus("CLOSED");
        SupportThread saved = threadRepository.save(thread);
        ThreadDto dto = toDto(saved);
        publishThreadUpdate(dto, true);
        return dto;
    }

    @PostMapping("/{threadId}/claim")
    public ThreadDto claimThread(@PathVariable String threadId) {
        String role = SecurityUtils.currentRole();
        if (!"SUPPORT".equals(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Not allowed");
        }
        String userId = SecurityUtils.currentUserId();
        SupportThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Thread not found"));
        if (!messageRepository.existsByThreadId(threadId)) {
            throw new ResponseStatusException(BAD_REQUEST, "No messages yet");
        }
        String assigned = thread.getAssignedSupportUserId();
        if (assigned != null && !assigned.equals(userId)) {
            throw new ResponseStatusException(CONFLICT, "Already assigned");
        }
        if (assigned == null || !assigned.equals(userId)) {
            thread.setAssignedSupportUserId(userId);
            SupportThread saved = threadRepository.save(thread);
            ThreadDto dto = toDto(saved);
            publishThreadUpdate(dto, true);
            publishClaimMessage(saved, userId);
            return dto;
        }
        return toDto(thread);
    }

    @GetMapping("/{threadId}/messages")
    public List<MessageDto> listMessages(@PathVariable String threadId) {
        SupportThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Thread not found"));
        if (!canAccessThread(thread)) {
            throw new ResponseStatusException(FORBIDDEN, "Not allowed");
        }

        List<SupportMessage> messages = messageRepository.findByThreadIdOrderBySentAtAsc(threadId);
        Map<String, User> users = loadUsers(messages.stream()
                .map(SupportMessage::getSenderUserId)
                .collect(Collectors.toSet()));

        return messages.stream()
                .sorted(Comparator.comparing(SupportMessage::getSentAt))
                .map(message -> {
                    User user = users.get(message.getSenderUserId());
                    return new MessageDto(
                            message.getId(),
                            message.getContent(),
                            message.getSentAt(),
                            message.getThreadId(),
                            message.getSenderUserId(),
                            displayName(user),
                            user == null ? null : user.getEmail()
                    );
                })
                .toList();
    }

    private boolean canAccessThread(SupportThread thread) {
        String role = SecurityUtils.currentRole();
        String userId = SecurityUtils.currentUserId();
        if ("SUPPORT".equals(role)) {
            String assigned = thread.getAssignedSupportUserId();
            return assigned == null || assigned.equals(userId);
        }
        return "CLIENT".equals(role) && thread.getCreatedByUserId().equals(userId);
    }

    private Map<String, User> loadUsers(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
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

    private ThreadDto toDto(SupportThread thread) {
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

    private void publishThreadUpdate(ThreadDto dto, boolean publishForSupport) {
        if (publishForSupport) {
            messagingTemplate.convertAndSend("/topic/threads", dto);
        }
        messagingTemplate.convertAndSend("/topic/users/" + dto.createdByUserId() + "/threads", dto);
    }

    private void publishClaimMessage(SupportThread thread, String supportUserId) {
        User supportUser = userRepository.findById(supportUserId).orElse(null);
        String supportName = displayName(supportUser);
        String label = supportName == null || supportName.isBlank()
                ? (supportUser == null ? "un agent" : supportUser.getEmail())
                : supportName;
        String content = "Votre ticket a ete pris en charge par " + label + ".";

        SupportMessage supportMessage = new SupportMessage();
        supportMessage.setContent(content);
        supportMessage.setThreadId(thread.getId());
        supportMessage.setSenderUserId(supportUserId);
        SupportMessage saved = messageRepository.save(supportMessage);

        ChatMessage payload = new ChatMessage(
                saved.getContent(),
                saved.getSentAt(),
                saved.getThreadId(),
                saved.getSenderUserId(),
                supportName,
                supportUser == null ? null : supportUser.getEmail()
        );
        messagingTemplate.convertAndSend("/topic/threads/" + thread.getId(), payload);
    }

    public record CreateThreadRequest(String subject, String reservationId) {
    }
}
