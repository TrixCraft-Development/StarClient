package net.modernclient.setting;

import java.util.LinkedHashMap;
import java.util.Map;

public class ColorModeSetting extends ModeSetting {
    private final Map<String, Integer> colors;

    public ColorModeSetting(String name, String description, String defaultMode, Map<String, Integer> colors) {
        super(name, description, defaultMode, colors.keySet().stream().toList());
        this.colors = colors;
    }

    public int getColor() {
        return colors.getOrDefault(getValue(), 0x00000000);
    }

    public Map<String, Integer> getColors() {
        return colors;
    }

    public static Map<String, Integer> ofPairs(String... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], (int) Long.parseLong(pairs[i + 1], 16));
        }
        return map;
    }
}