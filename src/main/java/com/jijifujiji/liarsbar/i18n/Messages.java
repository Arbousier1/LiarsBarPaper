package com.jijifujiji.liarsbar.i18n;

import com.jijifujiji.liarsbar.game.BetMode;
import com.jijifujiji.liarsbar.game.Card;
import com.jijifujiji.liarsbar.game.CardType;
import com.jijifujiji.liarsbar.game.GameState;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Messages {

    public static final String DEFAULT_LOCALE = "zh_cn";

    private final JavaPlugin plugin;
    private YamlConfiguration active;
    private YamlConfiguration fallback;
    private String locale = DEFAULT_LOCALE;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        saveBundledLanguage("messages_zh_cn.yml");
        saveBundledLanguage("messages_en_us.yml");

        locale = normalizeLocale(plugin.getConfig().getString("language", DEFAULT_LOCALE));
        fallback = YamlConfiguration.loadConfiguration(languageFile(DEFAULT_LOCALE));
        File selectedFile = languageFile(locale);
        active = selectedFile.exists() ? YamlConfiguration.loadConfiguration(selectedFile) : fallback;
    }

    public String locale() {
        return locale;
    }

    public String get(String key, Object... replacements) {
        return color(replace(raw(key), replacements));
    }

    public String plain(String key, Object... replacements) {
        return ChatColor.stripColor(get(key, replacements));
    }

    public List<String> list(String key, Object... replacements) {
        List<String> values = active.getStringList(key);
        if (values.isEmpty() && active != fallback) {
            values = fallback.getStringList(key);
        }
        List<String> colored = new ArrayList<>(values.size());
        for (String value : values) {
            colored.add(color(replace(value, replacements)));
        }
        return colored;
    }

    public String betMode(BetMode mode) {
        return get("bet-mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    public String card(Card card) {
        return get("card." + card.name().toLowerCase(Locale.ROOT));
    }

    public String cardType(CardType cardType) {
        return get("card-type." + cardType.name().toLowerCase(Locale.ROOT));
    }

    public String state(GameState state) {
        return get("state." + state.name().toLowerCase(Locale.ROOT));
    }

    private String raw(String key) {
        String value = active.getString(key);
        if (value == null && active != fallback) {
            value = fallback.getString(key);
        }
        return value == null ? key : value;
    }

    private String replace(String input, Object... replacements) {
        String output = input;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            output = output.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return output;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void saveBundledLanguage(String name) {
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private File languageFile(String selectedLocale) {
        return new File(plugin.getDataFolder(), "messages_" + selectedLocale + ".yml");
    }

    private String normalizeLocale(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return value.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
