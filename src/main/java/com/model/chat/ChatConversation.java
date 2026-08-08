package com.model.chat;

import java.time.LocalDateTime;

public class ChatConversation {

    public static final String TYPE_CUSTOMER_SUPPORT = "CUSTOMER_SUPPORT";
    public static final String TYPE_STAFF_DM = "STAFF_DM";

    private int conversationId;
    private String conversationType;
    private Integer customerUserId;
    private Integer staffUserIdA;
    private Integer staffUserIdB;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
    private boolean closed;

    public int getConversationId() { return conversationId; }
    public void setConversationId(int conversationId) { this.conversationId = conversationId; }

    public String getConversationType() { return conversationType; }
    public void setConversationType(String conversationType) { this.conversationType = conversationType; }

    public Integer getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(Integer customerUserId) { this.customerUserId = customerUserId; }

    public Integer getStaffUserIdA() { return staffUserIdA; }
    public void setStaffUserIdA(Integer staffUserIdA) { this.staffUserIdA = staffUserIdA; }

    public Integer getStaffUserIdB() { return staffUserIdB; }
    public void setStaffUserIdB(Integer staffUserIdB) { this.staffUserIdB = staffUserIdB; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }

    public boolean isCustomerSupport() {
        return TYPE_CUSTOMER_SUPPORT.equals(conversationType);
    }

    public boolean isStaffDm() {
        return TYPE_STAFF_DM.equals(conversationType);
    }
}
