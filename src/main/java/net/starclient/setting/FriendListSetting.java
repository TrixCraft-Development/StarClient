package net.starclient.setting;

import net.starclient.friend.FriendManager;

import java.util.List;

public class FriendListSetting extends Setting<List<String>> {

    public FriendListSetting(String name, String description) {
        super(name, description, FriendManager.getFriends());
    }

    @Override
    public List<String> getValue() {
        return FriendManager.getFriends();
    }

    public boolean addFriend(String username) {
        return FriendManager.addFriend(username);
    }

    public boolean addFriend(String username, String note) {
        return FriendManager.addFriend(username, note);
    }

    public boolean removeFriend(String username) {
        return FriendManager.removeFriend(username);
    }
}
