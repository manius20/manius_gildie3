package pl.s16.countries;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class S16Countries extends JavaPlugin implements Listener {
    private ConfigFiles configFiles;
    private NationStore store;
    private CurrencyService currency;
    private WarService warService;
    private MenuService menus;
    private BlueMapHook blueMapHook;
    private RegionStore regions;
    private RegionCommand regionCommand;

    @Override public void onEnable() {
        saveDefaultConfig();
        configFiles = new ConfigFiles(this);
        backupExistingData();
        store = new NationStore(this);
        regions = new RegionStore(this);
        regionCommand = new RegionCommand(this);
        currency = new CurrencyService(this);
        warService = new WarService(this);
        menus = new MenuService(this);
        GuildCommand guildCommand = new GuildCommand(this);
        PluginCommand cmd = getCommand("gildia");
        if (cmd == null) throw new IllegalStateException("Brakuje /gildia w plugin.yml");
        cmd.setExecutor(guildCommand);
        cmd.setTabCompleter(guildCommand);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(regionCommand, this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(warService, this);
        Bukkit.getScheduler().runTaskTimer(this, warService::tickWars, 20L, 20L);
        if (getServer().getPluginManager().isPluginEnabled("BlueMap") && configFiles.bool("bluemap.enabled")) {
            try {
                blueMapHook = new BlueMapHook(this);
                blueMapHook.hook();
            } catch (LinkageError error) {
                getLogger().warning("BlueMap API nie jest dostępne: " + error.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskTimer(this, this::updateBlueMap, 20L * 30, 20L * Math.max(5, getConfig().getInt("bluemap.refresh-seconds", 30)));
        getLogger().info("S16Countries gotowy: /gildia pomoc | Paper 26.2");
    }
    @Override public void onDisable() { if (store != null) store.save(); }

    private void backupExistingData() {
        File file = new File(getDataFolder(), "data.yml");
        if (!file.exists()) return;
        File dir = new File(getDataFolder(), "backups");
        if (!dir.exists() && !dir.mkdirs()) return;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        try { Files.copy(file.toPath(), new File(dir, "data-" + stamp + ".yml").toPath(), StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException e) { getLogger().warning("Nie wykonano kopii danych: " + e.getMessage()); }
    }

    public ConfigFiles config() { return configFiles; }
    public NationStore store() { return store; }
    public CurrencyService currency() { return currency; }
    public WarService wars() { return warService; }
    public MenuService menus() { return menus; }
    public RegionStore regions() { return regions; }
    public RegionCommand regionCommands() { return regionCommand; }
    public void reloadAll() { configFiles.reload(); currency.reload(); regions.reload(); updateBlueMap(); }
    public void updateBlueMap() { if (blueMapHook != null) blueMapHook.refresh(); }
    public void msg(CommandSender to, String content) {
        String prefix = configFiles.prefix();
        to.sendMessage(content.startsWith(prefix) ? content : prefix + content);
    }
    public void broadcastRaw(String content) {
        String prefix = configFiles.prefix();
        Bukkit.broadcastMessage(content.startsWith(prefix) ? content : prefix + content);
    }
    @EventHandler public void onKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;
        Nation n = store.of(killer.getUniqueId());
        if (n == null || n == store.of(victim.getUniqueId())) return;
        if (!getConfig().getBoolean("stats.count-kills", true)) return;
        if (n.kills < Integer.MAX_VALUE) n.kills++;
        store.save();
        updateBlueMap();
    }
    public void broadcast(String key, Object... placeholders) {
        if (configFiles.bool("war.broadcast-events") || !key.startsWith("war"))
            broadcastRaw(configFiles.message(key, placeholders));
    }
}
