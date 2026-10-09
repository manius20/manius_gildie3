package pl.s16.countries;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class ConfigFiles {
    private final JavaPlugin plugin;
    private YamlConfiguration roles, messages, gui;
    public ConfigFiles(JavaPlugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        plugin.reloadConfig();
        roles = load("roles.yml");
        messages = load("messages.yml");
        gui = load("gui.yml");
    }
    private YamlConfiguration load(String file) {
        File target = new File(plugin.getDataFolder(), file);
        if (!target.exists()) plugin.saveResource(file, false);
        return YamlConfiguration.loadConfiguration(target);
    }
    public YamlConfiguration roles() { return roles; }
    public YamlConfiguration gui() { return gui; }
    public String text(String message) { return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message); }
    public String prefix() { return text(plugin.getConfig().getString("chat.prefix", "&9&lS16 SMP 2 &8» &r")); }
    public String message(String key, Object... placeholders) {
        String template = messages.getString(key, key.equals("region-no-claim") ? "&cTeren administracyjny: nie można tutaj zakładać gildii ani claimować." : "&cBrak wiadomości: " + key);
        for (int i=0; i + 1 < placeholders.length; i += 2) template = template.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        return prefix() + text(template);
    }
    public String raw(String key, Object... placeholders) {
        String template = messages.getString(key, key.equals("region-no-claim") ? "&cTeren administracyjny: nie można tutaj zakładać gildii ani claimować." : "&cBrak wiadomości: " + key);
        for (int i=0; i + 1 < placeholders.length; i += 2) template = template.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        return text(template);
    }
    public boolean bool(String key) { return plugin.getConfig().getBoolean(key); }
    public int integer(String key) { return plugin.getConfig().getInt(key); }
    public long seconds(String key) { return Math.max(0L, plugin.getConfig().getLong(key)); }
}
