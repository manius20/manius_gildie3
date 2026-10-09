package pl.s16.countries;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** Samodzielne komendy admina i oznaczanie obszarów bez WorldEdit. */
public final class RegionCommand implements Listener {
    private record Selection(Location first, Location second) {}
    private final S16Countries plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, Location> first = new HashMap<>(), second = new HashMap<>();

    public RegionCommand(S16Countries plugin) { this.plugin = plugin; this.wandKey = new NamespacedKey(plugin,"admin-region-wand"); }
    private void send(CommandSender to, String msg) { plugin.msg(to, msg); }
    private boolean isWand(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer()
                .has(wandKey, PersistentDataType.BYTE);
    }
    @EventHandler public void onWand(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("s16countries.region.admin") || !isWand(e.getItem())) return;
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        e.setCancelled(true);
        Location loc = e.getClickedBlock().getLocation();
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            first.put(p.getUniqueId(), loc);
            send(p, "§bPunkt 1: §f" + coords(loc));
        } else {
            second.put(p.getUniqueId(), loc);
            send(p, "§bPunkt 2: §f" + coords(loc));
        }
    }
    @EventHandler public void onWandBreak(BlockBreakEvent e) {
        if (isWand(e.getPlayer().getInventory().getItemInMainHand())) e.setCancelled(true);
    }
    private String coords(Location loc) { return loc.getBlockX()+", "+loc.getBlockY()+", "+loc.getBlockZ(); }
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("s16countries.admin") || !sender.hasPermission("s16countries.region.admin")) {
            send(sender, "§cNie masz uprawnień do regionów."); return;
        }
        String sub = args.length < 3 ? "pomoc" : args[2].toLowerCase(Locale.ROOT);
        if (sub.equals("pomoc")) {
            for (String line : List.of("§bRegiony admina — blokują wyłącznie gildie i claimy:",
                    "§e/gildia admin region narzedzie §7— różdżka zaznaczania",
                    "§e/gildia admin region zaznacz-chunki <nazwa>",
                    "§e/gildia admin region zaznacz-bloki <nazwa>",
                    "§e/gildia admin region promien <nazwa> <chunki>",
                    "§e/gildia admin region pokaz <nazwa> §7— particle 8 s",
                    "§e/gildia admin region lista", "§e/gildia admin region usun <nazwa>")) send(sender, line);
            return;
        }
        if (sub.equals("lista")) {
            Collection<RegionStore.Region> regions = plugin.regions().all();
            send(sender, "§bRegiony ("+regions.size()+"): "+(regions.isEmpty()?"§7brak":"§f"+String.join("§7, §f", regions.stream().map(RegionStore.Region::name).toList())));
            return;
        }
        if (sub.equals("usun")) {
            if (args.length<4) {send(sender,"§cPodaj nazwę regionu.");return;}
            send(sender, plugin.regions().remove(args[3]) ? "§aUsunięto region §f"+args[3] : "§cNie znaleziono regionu / błąd zapisu.");
            return;
        }
        if (!(sender instanceof Player p)) {send(sender,"§cTa komenda wymaga gracza.");return;}
        if (sub.equals("narzedzie")) {
            Material mat=Material.matchMaterial(plugin.getConfig().getString("admin-regions.wand-material", "BLAZE_ROD"));
            if (mat == null || mat.isAir()) {send(p,"§cBłędny materiał różdżki w config.yml.");return;}
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§b§lS16 §f— Zaznaczanie regionów");
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte)1);
            item.setItemMeta(meta);
            var leftovers = p.getInventory().addItem(item);
            if (!leftovers.isEmpty()) leftovers.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(),i));
            send(p,"§aRóżdżka: §eLPM §7punkt 1, §ePPM §7punkt 2.");return;
        }
        if (sub.equals("pokaz")) {
            if (args.length<4) {send(p,"§cPodaj nazwę regionu.");return;}
            RegionStore.Region region=plugin.regions().get(args[3]);
            if (region==null) {send(p,"§cNie ma takiego regionu.");return;}
            if (!region.worldId().equals(p.getWorld().getUID())) {send(p,"§cRegion jest na innym świecie.");return;}
            show(p,region);
            send(p,"§bPokazuję granice regionu §f"+region.name()+" §bprzez kilka sekund.");return;
        }
        if (!sub.equals("promien") && !sub.equals("zaznacz-chunki") && !sub.equals("zaznacz-bloki")) {
            send(p,"§cNieznana komenda regionu. /gildia admin region pomoc");return;
        }
        if (args.length<4 || !args[3].matches("[A-Za-z0-9_-]{2,32}")) {
            send(p,"§cNazwa: 2–32 znaki (litery, liczby, _ lub -).");return;
        }
        RegionStore.Region region;
        if (sub.equals("promien")) {
            if (args.length<5) {send(p,"§c/gildia admin region promien <nazwa> <promien-w-chunkach>");return;}
            int radius;
            try {radius=Integer.parseInt(args[4]);} catch(NumberFormatException ex) {send(p,"§cPromień musi być liczbą.");return;}
            if (radius<0 || radius>Math.min(128,plugin.getConfig().getInt("admin-regions.max-radius-chunks",32))) {
                send(p,"§cNieprawidłowy promień (limit w config.yml).");return;
            }
            int cx=p.getLocation().getBlockX()>>4, cz=p.getLocation().getBlockZ()>>4;
            // Chunks snap here; negative coordinates handled by floorDiv.
            region=RegionStore.between(args[3],p.getWorld(),"CHUNKS",
                    (cx-radius)*16,(cz-radius)*16,(cx+radius)*16,(cz+radius)*16);
        } else {
            Location a=first.get(p.getUniqueId()), b=second.get(p.getUniqueId());
            if(a==null||b==null) {send(p,"§cZaznacz oba narożniki różdżką.");return;}
            if(a.getWorld()==null || !a.getWorld().equals(b.getWorld()) || !a.getWorld().equals(p.getWorld())) {
                send(p,"§cOba punkty muszą być na tym samym świecie co Ty.");return;
            }
            region=RegionStore.between(args[3], p.getWorld(), sub.equals("zaznacz-chunki")?"CHUNKS":"BLOCKS",
                    a.getBlockX(),a.getBlockZ(),b.getBlockX(),b.getBlockZ());
        }
        int limit=Math.max(1,plugin.getConfig().getInt("admin-regions.max-region-area-chunks",4096));
        if (RegionStore.chunkArea(region)>limit) {send(p,"§cRegion jest zbyt duży. Limit: §f"+limit+"§c chunków.");return;}
        if (plugin.regions().get(region.name())!=null) {send(p,"§cRegion o tej nazwie już istnieje.");return;}
        if (plugin.regions().overlaps(region)) {send(p,"§cRegion nachodzi na istniejący region admina.");return;}
        if (plugin.regions().containsClaim(region)) {send(p,"§cNie można utworzyć regionu: obejmuje już zajęte chunki gildii. Nic nie odebrano.");return;}
        if (plugin.regions().create(region))
            send(p,"§aZapisano region §f"+region.name()+" §7("+RegionStore.chunkArea(region)+" chunków, "+region.mode()+"). Można tutaj budować, ale nie claimować.");
        else send(p,"§cNie udało się zapisać regionu.");
    }
    private void show(Player p, RegionStore.Region r) {
        long period = Math.max(2,plugin.getConfig().getLong("admin-regions.particle-interval-ticks",10));
        long duration = Math.max(1,plugin.getConfig().getLong("admin-regions.particle-duration-seconds",8))*20;
        long count = Math.max(1,duration/period);
        final int y=p.getLocation().getBlockY();
        final Particle.DustOptions dust = new Particle.DustOptions(Color.AQUA, 1.2f);
        new BukkitRunnable() {
            long iterations=0;
            @Override public void run() {
                if (!p.isOnline() || !p.getWorld().getUID().equals(r.worldId()) || iterations++>=count) { cancel(); return; }
                // bounded render cost; only draw nearby samples
                int step=(int)Math.max(1,Math.ceil(Math.max((long)r.maxX()-r.minX(),(long)r.maxZ()-r.minZ())/100.0));
                for(long x=r.minX(); x<=r.maxX(); x+=step) {
                    dot(x, r.minZ()); dot(x, r.maxZ());
                }
                for(long z=r.minZ(); z<=r.maxZ(); z+=step) {
                    dot(r.minX(), z); dot(r.maxX(), z);
                }
            }
            void dot(long x,long z) {
                if(Math.abs((long)p.getLocation().getBlockX()-x)>96 || Math.abs((long)p.getLocation().getBlockZ()-z)>96) return;
                p.spawnParticle(Particle.DUST, x+0.5,y+1.2,z+0.5,1,0,0,0,0,dust);
            }
        }.runTaskTimer(plugin,0L,period);
    }
}
