package com.example.managing.utils;

import com.example.managing.model.ManagingMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;

public class ParseManagingMessage {
    public static ManagingMessage parsePostBody(String postBody) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(postBody);
            if(!rootNode.has("nodes") && rootNode.has("destinationId")) {
                return new ManagingMessage(rootNode.get("destinationId").asText());
            }
            else if(rootNode.has("nodes") && !rootNode.has("destinationId")) {
                return new ManagingMessage(new ObjectMapper().readValue(rootNode.get("nodes").toString(), ArrayList.class));
            }
            else{
                return new ManagingMessage(rootNode.get("destinationId").asText(), new ObjectMapper().readValue(rootNode.get("nodes").toString(), ArrayList.class));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
