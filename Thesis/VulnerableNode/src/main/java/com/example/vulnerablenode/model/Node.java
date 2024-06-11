package com.example.vulnerablenode.model;

import java.util.concurrent.ConcurrentHashMap;

public class Node {
    private String id;
    private int type;
    private boolean isInfected;

    private ConcurrentHashMap<String, ForwardRow> nodeTable;

    public Node(String id, int type, boolean isInfected, ConcurrentHashMap<String, ForwardRow> nodeTable) {
        this.id = id;
        this.type = type;
        this.isInfected = isInfected;
        this.nodeTable = nodeTable;
    }

    //getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isInfected() {
        return isInfected;
    }

    public void setInfected(boolean infected) {
        isInfected = infected;
    }

    public ConcurrentHashMap<String, ForwardRow> getNodeTable() {
        return nodeTable;
    }

    public void setNodeTable(ConcurrentHashMap<String, ForwardRow> nodeTable) {
        this.nodeTable = nodeTable;
    }


}
