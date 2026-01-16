package com.ycwy.poc_chat.chat;

import java.time.Instant;

public class ChatMessage {

    private String content;
    private Instant sentAt;
    private String threadId;
    private String senderUserId;
    private String senderName;
    private String senderEmail;

    public ChatMessage() {
    }

    public ChatMessage(String content, Instant sentAt) {
        this.content = content;
        this.sentAt = sentAt;
    }

    public ChatMessage(String content, Instant sentAt, String threadId, String senderUserId) {
        this.content = content;
        this.sentAt = sentAt;
        this.threadId = threadId;
        this.senderUserId = senderUserId;
    }

    public ChatMessage(
            String content,
            Instant sentAt,
            String threadId,
            String senderUserId,
            String senderName,
            String senderEmail
    ) {
        this.content = content;
        this.sentAt = sentAt;
        this.threadId = threadId;
        this.senderUserId = senderUserId;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(String senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }
}
