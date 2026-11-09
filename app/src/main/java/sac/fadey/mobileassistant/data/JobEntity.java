package sac.fadey.mobileassistant.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "jobs")
public class JobEntity {
    @PrimaryKey @NonNull
    public String id = "";
    public String type;
    public String module;
    public String status;
    public String payloadJson;
    public String resultJson;
    public long createdAt;
    public long updatedAt;
}
