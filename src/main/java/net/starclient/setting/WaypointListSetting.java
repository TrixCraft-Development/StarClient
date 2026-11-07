package net.starclient.setting;

import java.util.ArrayList;
import java.util.List;

public class WaypointListSetting extends Setting<List<String>> {

    public WaypointListSetting(String name, String description) {
        super(name, description, new ArrayList<>());
    }
}
