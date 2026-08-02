package com.techy.noti_filter.dao;

import com.techy.noti_filter.db.NotificationEntity;
import com.techy.noti_filter.model.NotificationData;

public class NotificationDataMapper {

    public static NotificationData toData(NotificationEntity d) {

        NotificationData e = new NotificationData();

        e.title = d.title;
        e.body = d.body;
        e.sender = d.sender;
        e.category = d.category;
        e.priority = d.priority;

        e.notificationKey=d.notificationKey;
        e.packageName=d.packageName;
        e.appIcon = d.appIcon;
        e.actionTaken = d.actionTaken;
        e.actionCount = d.actionCount;
        e.hasMedia = d.hasMedia;

        e.channelId = d.channel;
        e.hour = d.hour;
        e.day = d.day;

        e.app = d.app;
        e.type = d.type;

        e.titleLength = d.titleLen;
        e.bodyLength = d.bodyLen;

        e.timeToInteract = d.timeToInteract;
        e.label = d.label;

        e.postTime = d.postTime;

        return e;
    }
}
