package glorydark.joinitem.languages;

import cn.nukkit.utils.Config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class PluginLanguage {
    private final Map<String, String> data = new HashMap<>();

    public PluginLanguage(File file) {
        if (file.exists()) {
            Config config = new Config(file, Config.PROPERTIES);
            for (Map.Entry<String, Object> objectEntry : config.getAll().entrySet()) {
                data.put(objectEntry.getKey(), (String) objectEntry.getValue());
            }
        }
    }

    public String getTranslation(String key, Object... params) {
        String s = data.getOrDefault(key, "null").replace("\\n", "\n");
        for (int i = 1; i <= params.length; i++) {
            s = s.replaceAll("%" + i + "%", params[i - 1].toString());
        }
        return s;
    }
}
