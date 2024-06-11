package com.example.vulnerablenode.model.Message;

public class Message {

    private String senderId;
    private String destinationId;
    private int messageType;
    private Object messageContent;

    public Message(String senderId, String destinationId, int messageType, Object messageContent) {
        this.senderId = senderId;
        this.destinationId = destinationId;
        this.messageType = messageType;
        this.messageContent = messageContent;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public int getMessageType() {
        return messageType;
    }

    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    public Object getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(Object messageContent) {
        this.messageContent = messageContent;
    }

}
