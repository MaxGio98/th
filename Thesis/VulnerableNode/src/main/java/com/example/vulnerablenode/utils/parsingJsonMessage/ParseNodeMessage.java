package com.example.vulnerablenode.utils.parsingJsonMessage;

import com.example.vulnerablenode.model.Message.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ParseNodeMessage {
    public static Message parsePostBody(String postBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(postBody);
            String senderId = rootNode.get("senderId").asText();
            String receiverId = rootNode.get("destinationId").asText();
            int messageType = rootNode.get("messageType").asInt();
            Object messageContent = rootNode.get("messageContent");
            return new Message(senderId, receiverId, messageType, messageContent);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
