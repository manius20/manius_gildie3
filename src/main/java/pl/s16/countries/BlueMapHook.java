package pl.s16.countries;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.util.*;

/** Markery chunków z informacjami i lekkim rankingiem terenowym w okienku BlueMap. */
public final class BlueMapHook {
    private final S16Countries plugin;
    public BlueMapHook(S16Countries plugin) { this.plugin=plugin; }
    public void hook() {
        BlueMapAPI.onEnable(api -> Bukkit.getScheduler().runTask(plugin, this::refresh));
        refresh();
    }
    public void refresh() {
        if (!plugin.config().bool("bluemap.enabled")) return;
        BlueMapAPI.getInstance().ifPresent(api -> {
            String id=plugin.getConfig().getString("bluemap.marker-set-id", "s16countries");
            List<String> colors=plugin.getConfig().getStringList("bluemap.colors");
            if(colors.isEmpty()) colors=List.of("#e74c3c");
            List<Nation> ranking = new ArrayList<>(plugin.store().all());
            ranking.sort(Comparator.comparingInt((Nation n)->n.claims.size()).reversed().thenComparing(n -> n.name.toLowerCase(Locale.ROOT)));
            Map<Nation,Integer> placement = new IdentityHashMap<>();
            for (int i=0; i<ranking.size(); i++) placement.put(ranking.get(i),i+1);
            Map<Nation,String> details = new IdentityHashMap<>();
            for (Nation n : ranking) details.put(n, detail(n, ranking, placement.get(n)));
            for (World world : Bukkit.getWorlds()) {
                MarkerSet set=MarkerSet.builder().label(plugin.getConfig().getString("bluemap.marker-set-label","S16 SMP 2 — Państwa")).build();
                int nationIndex=0;
                for (Nation n:plugin.store().all()) {
                    String hex=colors.get(nationIndex++ % colors.size());
                    int rgb;
                    try { rgb=Integer.parseInt(hex.replace("#",""),16); }
                    catch (NumberFormatException ex) { rgb=0x3498db; }
                    float lineAlpha=(float)plugin.getConfig().getDouble("bluemap.line-alpha",0.9);
                    float fillAlpha=(float)plugin.getConfig().getDouble("bluemap.fill-alpha",0.18);
                    for (ClaimKey key:n.claims) {
                        if (!key.world().equals(world.getUID())) continue;
                        long x=(long)key.x()*16,z=(long)key.z()*16;
                        ShapeMarker marker=ShapeMarker.builder().label(n.name)
                                .detail(details.get(n))
                                .shape(Shape.createRect(x,z,x+16,z+16),(float)plugin.getConfig().getDouble("bluemap.marker-y",70))
                                .lineColor(new Color(rgb,lineAlpha)).fillColor(new Color(rgb,fillAlpha))
                                .lineWidth(plugin.getConfig().getInt("bluemap.line-width",2)).build();
                        set.getMarkers().put("chunk-"+key.x()+"-"+key.z(),marker);
                    }
                }
                api.getWorld(world).ifPresent(blueWorld -> {
                    for(BlueMapMap map : blueWorld.getMaps()) map.getMarkerSets().put(id,set);
                });
            }
        });
    }
    private String detail(Nation nation, List<Nation> ranking, int rank) {
        List<String> online = new ArrayList<>(), offline = new ArrayList<>();
        for (UUID id : nation.members.keySet()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(id);
            String name = member.getName() == null ? id.toString().substring(0,8) : member.getName();
            String safe = escape(name);
            if (Bukkit.getPlayer(id) != null) online.add(safe); else offline.add(safe);
        }
        online.sort(String.CASE_INSENSITIVE_ORDER);
        offline.sort(String.CASE_INSENSITIVE_ORDER);
        int listed = Math.max(0, Math.min(100, plugin.getConfig().getInt("bluemap.max-listed-members",30)));
        StringBuilder sb = new StringBuilder("<div style='min-width:200px;max-width:300px'>");
        sb.append("<strong style='color:#62b0ff'>").append(escape(nation.name)).append("</strong>");
        sb.append("<table style='margin:7px 0;width:100%;font-size:12px'>")
          .append("<tr><td>Ranking terenów</td><td><b>#").append(rank).append("</b></td></tr>")
          .append("<tr><td>Chunki</td><td><b>").append(nation.claims.size()).append("</b></td></tr>")
          .append("<tr><td>Zabójstwa</td><td><b>").append(nation.kills).append("</b></td></tr>")
          .append("<tr><td>Członkowie</td><td>").append(nation.members.size()).append("</td></tr>")
          .append("<tr><td>Online / Offline</td><td><span style='color:#40da71'>")
          .append(online.size()).append("</span> / <span style='color:#ff6464'>")
          .append(offline.size()).append("</span></td></tr></table>");
        int shown=0;
        sb.append("<details style='margin:5px 0'><summary style='font-size:12px;cursor:pointer'>Lista graczy</summary>");
        if (!online.isEmpty()) {
            sb.append("<div style='color:#40da71;font-size:12px'>");
            for (String name:online) if(shown++<listed) sb.append("● ").append(name).append(" ");
            sb.append("</div>");
        }
        for (String name:offline) if (shown++<listed) {
            sb.append("<span style='color:#ff6464;font-size:12px'>● ").append(name).append(" </span>");
        }
        if (shown > listed) sb.append("<div style='font-size:11px'>+ ").append(shown-listed).append(" pozostałych</div>");
        sb.append("</details>");
        int top = Math.max(0,Math.min(10,plugin.getConfig().getInt("bluemap.show-top",3)));
        if (top>0) {
            sb.append("<hr/><div style='font-size:12px'><b>TOP ").append(Math.min(top,ranking.size())).append(" terenów</b><br/>");
            for(int i=0;i<Math.min(top,ranking.size());i++) {
                Nation n=ranking.get(i);
                sb.append(i+1).append(". ").append(escape(n.name)).append(" — ").append(n.claims.size()).append(" chunków<br/>");
            }
            sb.append("</div>");
        }
        return sb.append("</div>").toString();
    }
    private static String escape(String input) {
        return input.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
}
