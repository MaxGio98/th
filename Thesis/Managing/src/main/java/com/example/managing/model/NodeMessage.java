package com.example.managing.model;

public class NodeMessage {

    private String senderId;
    private String destinationId;
    //0: add neighbor
    //1: discovery
    //2: ping
    //3: add Neighbor
    //4: reset neighbors
    //5: add node
    //6: remove node
    private int messageType;
    private Object messageContent;

    public NodeMessage(String senderId, String destinationId, int messageType, Object messageContent) {
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
