package com.example.managing.model;

public class ImproveTime {
    //start time
    public long startTime;
    //end time
    public long endTime;
    public double initialNetworkHealth;

    public ImproveTime(long startTime, long endTime, double initialNetworkHealth) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.initialNetworkHealth = initialNetworkHealth;
    }
}
