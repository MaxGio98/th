package com.example.managing.model;

public class ForwardRow {

    public String nextHop;
    public int distance;

    public ForwardRow(String nextHop, int distance) {
        this.nextHop = nextHop;
        this.distance = distance;
    }
}
