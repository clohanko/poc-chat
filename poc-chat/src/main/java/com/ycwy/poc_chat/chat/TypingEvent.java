package com.ycwy.poc_chat.chat;

public class TypingEvent {

    private String threadId;
    private String senderUserId;
    private String senderName;
    private String senderEmail;
    private boolean typing;

    public TypingEvent() {
    }

    public TypingEvent(
            String threadId,
            String senderUserId,
            String senderName,
            String senderEmail,
            boolean typing
    ) {
        this.threadId = threadId;
        this.senderUserId = senderUserId;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.typing = typing;
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

    public boolean isTyping() {
        return typing;
    }

    public void setTyping(boolean typing) {
        this.typing = typing;
    }
}
