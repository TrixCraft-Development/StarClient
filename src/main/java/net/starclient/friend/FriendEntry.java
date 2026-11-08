package net.starclient.friend;

import com.google.gson.annotations.SerializedName;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Represents a friend entry with an optional personal note/tag (e.g. "2v2 Partner", "SMP Builder").
 */
public class FriendEntry {

    @SerializedName("name")
    private String name;

    @SerializedName("note")
    private String note;

    private transient ResolvableProfile cachedProfile;

    public FriendEntry() {
        this("", "");
    }

    public FriendEntry(String name) {
        this(name, "");
    }

    public FriendEntry(String name, String note) {
        this.name = name != null ? name.trim() : "";
        this.note = note != null ? note.trim() : "";
    }

    public String getName() {
        return name != null ? name : "";
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
        this.cachedProfile = null;
    }

    public String getNote() {
        return note != null ? note : "";
    }

    public void setNote(String note) {
        this.note = note != null ? note.trim() : "";
    }

    public ResolvableProfile getResolvableProfile() {
        if (cachedProfile == null && !getName().isBlank()) {
            cachedProfile = ResolvableProfile.createUnresolved(getName());
        }
        return cachedProfile;
    }
}
