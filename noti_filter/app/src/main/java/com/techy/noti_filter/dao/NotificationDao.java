package com.techy.noti_filter.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.techy.noti_filter.db.NotificationEntity;

import java.util.List;

@Dao
public interface NotificationDao {

    @Insert
    long insert(NotificationEntity entity);

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    List<NotificationEntity> getAll();

    @Query("SELECT * FROM notifications WHERE label = :label")
    List<NotificationEntity> getByLabel(int label);

    @Query("DELETE FROM notifications")
    void deleteAll();
}