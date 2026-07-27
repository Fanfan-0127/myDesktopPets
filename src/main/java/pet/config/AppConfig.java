package pet.config;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.Map;

public final class AppConfig {

    private static final String CONFIG_FILE = "application.yml";
    private static final AppConfig INSTANCE = new AppConfig();

    private final float animSpeed;
    private final float moveSpeed;
    private final float petScale;
    private final float sleepTimeout;
    private final float sleepChance;
    private final float relaxMin;
    private final float relaxMax;
    private final float moveChance;
    private final float specialChance;
    private final float ambientVoiceInterval;
    private final boolean interactive;
    private final boolean voiceEnabled;
    private final boolean alwaysOnTop;
    private final int pad;
    private final int rightPadExtra;

    private AppConfig() {
        Map<String, Object> root = loadYaml();
        Map<String, Object> pet = getMap(root, "pet");

        animSpeed = getFloat(pet, "anim-speed", 1.0f);
        moveSpeed = getFloat(pet, "move-speed", 100f);
        petScale = getFloat(pet, "pet-scale", 0.5f);
        sleepTimeout = getFloat(pet, "sleep-timeout", 30f);
        sleepChance = getFloat(pet, "sleep-chance", 0.05f);
        relaxMin = getFloat(pet, "relax-min", 2f);
        relaxMax = getFloat(pet, "relax-max", 4f);
        moveChance = getFloat(pet, "move-chance", 1.0f);
        specialChance = getFloat(pet, "special-chance", 0.02f);
        ambientVoiceInterval = getFloat(pet, "ambient-voice-interval", 50f);
        interactive = getBoolean(pet, "interactive", true);
        voiceEnabled = getBoolean(pet, "voice-enabled", true);
        alwaysOnTop = getBoolean(pet, "always-on-top", true);

        Map<String, Object> window = getMap(root, "window");
        pad = getInt(window, "pad", 30);
        rightPadExtra = getInt(window, "right-pad-extra", 10);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml() {
        Yaml yaml = new Yaml();
        InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
        if (in == null) {
            throw new IllegalStateException("Config file not found: " + CONFIG_FILE);
        }
        return yaml.load(in);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw new IllegalStateException("Missing or invalid config section: " + key);
    }

    private static float getFloat(Map<String, Object> map, String key, float defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    // ---- singleton accessor ----
    public static AppConfig getInstance() {
        return INSTANCE;
    }

    // ---- getters ----
    public float getAnimSpeed() { return animSpeed; }
    public float getMoveSpeed() { return moveSpeed; }
    public float getPetScale() { return petScale; }
    public float getSleepTimeout() { return sleepTimeout; }
    public float getSleepChance() { return sleepChance; }
    public float getRelaxMin() { return relaxMin; }
    public float getRelaxMax() { return relaxMax; }
    public float getMoveChance() { return moveChance; }
    public float getSpecialChance() { return specialChance; }
    public float getAmbientVoiceInterval() { return ambientVoiceInterval; }
    public boolean isInteractive() { return interactive; }
    public boolean isVoiceEnabled() { return voiceEnabled; }
    public boolean isAlwaysOnTop() { return alwaysOnTop; }
    public int getPad() { return pad; }
    public int getRightPadExtra() { return rightPadExtra; }
}
