package pl.s16.countries;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Wszystkie mutacje tylko na głównym wątku Paper. Dane zapisywane atomowo. */
public final class NationStore {
    private final S16Countries plugin;
    private final File file;
    private final Map<String, Nation> nations = new LinkedHashMap<>();
    private final Map<UUID, Nation> byMember = new HashMap<>();
    private final Map<ClaimKey, Nation> byChunk = new HashMap<>();
    public final List<War> wars = new ArrayList<>();

    public NationStore(S16Countries plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }
    public Collection<Nation> all() { return Collections.unmodifiableCollection(nations.values()); }
    public Nation byName(String name) { return nations.get(name.toLowerCase(Locale.ROOT)); }
    public Nation of(UUID player) { return byMember.get(player); }
    public Nation at(ClaimKey key) { return byChunk.get(key); }
    public Nation at(Location loc) { return at(ClaimKey.of(loc)); }

    public void put(Nation nation) {
        nations.put(nation.name.toLowerCase(Locale.ROOT), nation);
        nation.members.keySet().forEach(id -> byMember.put(id, nation));
        nation.claims.forEach(key -> byChunk.put(key, nation));
        save();
    }
    public void addMember(Nation n, UUID id, String role) { n.members.put(id, role); byMember.put(id, n); save(); }
    public void removeMember(Nation n, UUID id) { n.members.remove(id); byMember.remove(id); save(); }
    public void claim(Nation n, ClaimKey key) {
        Nation existing = byChunk.get(key);
        if (existing != null) {
            existing.claims.remove(key);
            if (existing.home != null && key.equals(ClaimKey.of(existing.home))) existing.home = null;
        }
        n.claims.add(key);
        byChunk.put(key, n);
        save();
        plugin.updateBlueMap();
    }
    public void unclaim(Nation n, ClaimKey key) {
        n.claims.remove(key);
        byChunk.remove(key);
        save();
        plugin.updateBlueMap();
    }
    public void delete(Nation n) {
        nations.remove(n.name.toLowerCase(Locale.ROOT));
        n.members.keySet().forEach(byMember::remove);
        n.claims.forEach(byChunk::remove);
        wars.removeIf(w -> w.involves(n));
        nations.values().forEach(other -> other.allies.remove(n.name.toLowerCase(Locale.ROOT)));
        save();
        plugin.updateBlueMap();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection all = yaml.getConfigurationSection("nations");
        if (all != null) for (String id : all.getKeys(false)) {
            try {
                String root = "nations." + id + ".";
                UUID leader = UUID.fromString(Objects.requireNonNull(yaml.getString(root + "leader")));
                Nation n = new Nation(yaml.getString(root + "name", id), leader,
                        plugin.config().roles().getString("settings.leader-role", "prezydent"),
                        yaml.getBoolean(root + "friendly-fire"));
                n.members.clear();
                ConfigurationSection members = yaml.getConfigurationSection(root + "members");
                if (members != null) for (String uuid : members.getKeys(false)) {
                    n.members.put(UUID.fromString(uuid), members.getString(uuid, "obywatel"));
                }
                n.members.putIfAbsent(leader, plugin.config().roles().getString("settings.leader-role", "prezydent"));
                for (String claim : yaml.getStringList(root + "claims")) n.claims.add(ClaimKey.parse(claim));
                n.home = yaml.getLocation(root + "home");
                n.allies.addAll(yaml.getStringList(root + "allies"));
                n.lastWarEnded = yaml.getLong(root + "last-war-ended");
                n.kills = Math.max(0, yaml.getInt(root + "kills", 0));
                nations.put(n.name.toLowerCase(Locale.ROOT), n);
                for (UUID uuid : n.members.keySet()) byMember.put(uuid, n);
                for (ClaimKey key : n.claims) {
                    if (byChunk.putIfAbsent(key, n) != null) plugin.getLogger().warning("Zdublowany claim w data.yml: " + key);
                }
            } catch (Exception ex) { plugin.getLogger().severe("Nie można odczytać gildii " + id + ": " + ex.getMessage()); }
        }
        ConfigurationSection warSection = yaml.getConfigurationSection("wars");
        if (warSection != null) for (String id : warSection.getKeys(false)) {
            try {
                String base = "wars." + id + ".";
                Nation a = byName(Objects.requireNonNull(yaml.getString(base + "a")));
                Nation b = byName(Objects.requireNonNull(yaml.getString(base + "b")));
                if (a != null && b != null) wars.add(new War(a, b, yaml.getLong(base + "start"), yaml.getLong(base + "end"),
                        yaml.getBoolean(base + "peace-a"), yaml.getBoolean(base + "peace-b")));
            } catch (Exception ex) { plugin.getLogger().warning("Niepoprawny zapis wojny " + id); }
        }
    }
    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Nation n : nations.values()) {
            // Bezpieczny klucz nawet jeśli admin zmieni name-pattern i dopuści kropki.
            String safeId = Base64.getUrlEncoder().withoutPadding().encodeToString(n.name.getBytes(StandardCharsets.UTF_8));
            String root = "nations." + safeId + ".";
            yaml.set(root + "name", n.name);
            yaml.set(root + "leader", n.leader.toString());
            yaml.set(root + "friendly-fire", n.friendlyFire);
            yaml.set(root + "home", n.home);
            yaml.set(root + "last-war-ended", n.lastWarEnded);
            yaml.set(root + "kills", n.kills);
            for (var entry : n.members.entrySet()) yaml.set(root + "members." + entry.getKey(), entry.getValue());
            yaml.set(root + "claims", n.claims.stream().map(ClaimKey::serialize).toList());
            yaml.set(root + "allies", new ArrayList<>(n.allies));
        }
        for (int i=0; i<wars.size(); i++) {
            War w = wars.get(i);
            String root = "wars." + i + ".";
            yaml.set(root + "a", w.a.name);
            yaml.set(root + "b", w.b.name);
            yaml.set(root + "start", w.start);
            yaml.set(root + "end", w.end);
            yaml.set(root + "peace-a", w.peaceA);
            yaml.set(root + "peace-b", w.peaceB);
        }
        try {
            File temp = new File(file.getParentFile(), "data.yml.tmp");
            yaml.save(temp);
            try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ex) { plugin.getLogger().severe("BŁĄD ZAPISU data.yml: " + ex.getMessage()); }
    }
}
