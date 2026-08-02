package com.techy.noti_filter.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications")
public class NotificationEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String packageName;
    public String notificationKey;
    public byte[] appIcon;
    public String title;
    public String body;
    public String sender;
    public String category;
    public int priority;

    public String actionTaken;
    public int actionCount;
    public boolean hasMedia;

    public String channel;
    public int hour;
    public int day;

    public String app;
    public String type;

    public int titleLen;
    public int bodyLen;

    public long timeToInteract;

    public int label;

    public long postTime;
}