package sac.fadey.mobileassistant.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface JobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(JobEntity job);

    @Update
    void update(JobEntity job);

    @Query("SELECT * FROM jobs ORDER BY createdAt DESC LIMIT 200")
    List<JobEntity> recent();

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    JobEntity byId(String id);

    @Query("UPDATE jobs SET status = :status, resultJson = :resultJson, updatedAt = :updatedAt WHERE id = :id")
    void updateStatus(String id, String status, String resultJson, long updatedAt);
}
