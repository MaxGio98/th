package com.example.managing.model;

import java.util.ArrayList;

public class ManagingMessage {
    private String destinationId;
    private ArrayList<String> nodes;

    public ManagingMessage(String destinationId, ArrayList<String> nodes) {
        this.destinationId = destinationId;
        this.nodes = nodes;
    }
    public ManagingMessage(ArrayList<String> nodes) {
        this.nodes = nodes;
    }

    public ManagingMessage(String destinationId) {
        this.destinationId = destinationId;
        this.nodes = new ArrayList<>();
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public ArrayList<String> getNodes() {
        return nodes;
    }

    public void setNodes(ArrayList<String> nodes) {
        this.nodes = nodes;
    }

}
