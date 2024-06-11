package com.example.managing.model;
import java.util.ArrayList;


public class NodeStatus {
    private boolean isReady;
    private ArrayList<String> neighbors;
    private boolean isInfected;
    private int type;



    public NodeStatus(boolean isReady, ArrayList<String> neighbors, boolean isInfected, int type) {
        this.isReady = isReady;
        this.neighbors = neighbors;
        this.isInfected = isInfected;
        this.type = type;
    }

    public boolean isReady() {
        return isReady;
    }

    public void setIsReady(boolean ready) {
        isReady = ready;
    }

    public ArrayList<String> getNeighbors() {
        return neighbors;
    }

    public void setNeighbors(ArrayList<String> neighbors) {
        this.neighbors = neighbors;
    }

    public boolean isInfected() {
        return isInfected;
    }

    public void setInfected(boolean infected) {
        this.isInfected = infected;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
