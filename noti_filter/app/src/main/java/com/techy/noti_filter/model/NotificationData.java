package com.techy.noti_filter.model;

import java.io.Serializable;

public class NotificationData implements Serializable {

    public String notificationKey;
    public String packageName;

    public byte[] appIcon;

    public String title;
    public String body;
    public String subText;
    public String sender;

    public long timestamp;

    public String category;
    public int priority;

    public int actionCount;

    public String actionTaken;
    public int badgeCount;

    public boolean hasMedia;
    public String soundType;
    public String channelId;


    public int hour;
    public int day;
    public String app;

    public int label;
    public String type;

    public long timeToInteract;
    public int titleLength;
    public int bodyLength;
public long postTime;
}