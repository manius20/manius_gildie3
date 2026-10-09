package pl.s16.countries;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** Regiony wyłącznie administracyjne: blokada założenia państwa/claimowania, bez blokad budowania. */
public final class RegionStore {
    public record Region(String name, UUID worldId, String worldName, String mode, int minX, int maxX, int minZ, int maxZ) {
        public boolean intersects(Region other) {
            return worldId.equals(other.worldId) && minX <= other.maxX && maxX >= other.minX
                    && minZ <= other.maxZ && maxZ >= other.minZ;
        }
        public boolean intersectsChunk(ClaimKey key) {
            if (!worldId.equals(key.world())) return false;
            long x = (long) key.x() * 16, z = (long) key.z() * 16;
            return x <= maxX && x + 15 >= minX && z <= maxZ && z + 15 >= minZ;
        }
    }
    private final S16Countries plugin;
    private final File file;
    private final Map<String, Region> regions = new LinkedHashMap<>();

    public RegionStore(S16Countries plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regions.yml");
        reload();
    }
    public Collection<Region> all() { return Collections.unmodifiableCollection(regions.values()); }
    public Region get(String name) { return regions.get(name.toLowerCase(Locale.ROOT)); }
    public boolean blocks(ClaimKey key) {
        return plugin.getConfig().getBoolean("admin-regions.enabled", true)
                && regions.values().stream().anyMatch(r -> r.intersectsChunk(key));
    }
    public boolean create(Region next) {
        String id = next.name.toLowerCase(Locale.ROOT);
        if (regions.containsKey(id)) return false;
        for (Region existing : regions.values()) if (existing.intersects(next)) return false;
        regions.put(id, next);
        if (!save()) { regions.remove(id); return false; }
        return true;
    }
    public boolean remove(String name) {
        String id = name.toLowerCase(Locale.ROOT);
        Region removed = regions.remove(id);
        if (removed == null) return false;
        if (!save()) { regions.put(id, removed); return false; }
        return true;
    }
    public boolean containsClaim(Region region) {
        for (Nation nation : plugin.store().all()) {
            for (ClaimKey claim : nation.claims) if (region.intersectsChunk(claim)) return true;
        }
        return false;
    }
    public boolean overlaps(Region region) {
        return regions.values().stream().anyMatch(r -> r.intersects(region) || chunkBoundsIntersect(r, region));
    }
    private static boolean chunkBoundsIntersect(Region a, Region b) {
        return a.worldId.equals(b.worldId)
                && Math.floorDiv(a.minX, 16) <= Math.floorDiv(b.maxX, 16)
                && Math.floorDiv(a.maxX, 16) >= Math.floorDiv(b.minX, 16)
                && Math.floorDiv(a.minZ, 16) <= Math.floorDiv(b.maxZ, 16)
                && Math.floorDiv(a.maxZ, 16) >= Math.floorDiv(b.minZ, 16);
    }
    public void reload() {
        regions.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("regions");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            try {
                String prefix = "regions." + id + ".";
                UUID world = UUID.fromString(Objects.requireNonNull(yaml.getString(prefix + "world-uuid")));
                Region region = new Region(
                        Objects.requireNonNull(yaml.getString(prefix + "name")), world,
                        yaml.getString(prefix + "world-name", ""),
                        yaml.getString(prefix + "mode", "BLOCKS"),
                        yaml.getInt(prefix + "min-x"), yaml.getInt(prefix + "max-x"),
                        yaml.getInt(prefix + "min-z"), yaml.getInt(prefix + "max-z"));
                if (region.minX > region.maxX || region.minZ > region.maxZ || overlaps(region))
                    throw new IllegalArgumentException("niepoprawny lub nakładający się region");
                regions.put(region.name.toLowerCase(Locale.ROOT), region);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Pominięto region " + id + ": " + ex.getMessage());
            }
        }
    }
    private boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Region region : regions.values()) {
            String id = Base64.getUrlEncoder().withoutPadding().encodeToString(region.name.getBytes(StandardCharsets.UTF_8));
            String root = "regions." + id + ".";
            yaml.set(root + "name", region.name);
            yaml.set(root + "world-uuid", region.worldId.toString());
            yaml.set(root + "world-name", region.worldName);
            yaml.set(root + "mode", region.mode);
            yaml.set(root + "min-x", region.minX);
            yaml.set(root + "max-x", region.maxX);
            yaml.set(root + "min-z", region.minZ);
            yaml.set(root + "max-z", region.maxZ);
        }
        try {
            File temp = new File(file.getParentFile(), "regions.yml.tmp");
            yaml.save(temp);
            try { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie zapisano regions.yml: " + ex.getMessage());
            return false;
        }
    }
    public static Region between(String name, World world, String mode, int ax, int az, int bx, int bz) {
        int minX = Math.min(ax, bx), maxX = Math.max(ax, bx), minZ = Math.min(az, bz), maxZ = Math.max(az, bz);
        if (mode.equals("CHUNKS")) {
            minX = Math.floorDiv(minX, 16) * 16; maxX = Math.floorDiv(maxX, 16) * 16 + 15;
            minZ = Math.floorDiv(minZ, 16) * 16; maxZ = Math.floorDiv(maxZ, 16) * 16 + 15;
        }
        return new Region(name, world.getUID(), world.getName(), mode, minX, maxX, minZ, maxZ);
    }
    public static long chunkArea(Region r) {
        return ((long)Math.floorDiv(r.maxX,16) - Math.floorDiv(r.minX,16) + 1)
                * ((long)Math.floorDiv(r.maxZ,16) - Math.floorDiv(r.minZ,16) + 1);
    }
}
