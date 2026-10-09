package pl.s16.countries;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public final class GuildCommand implements CommandExecutor, TabCompleter {
    private final S16Countries plugin;
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<String, Long> allianceOffers = new HashMap<>();
    private final Map<UUID, Long> disbandConfirm = new HashMap<>();
    private final Map<UUID, Long> homeCooldown = new HashMap<>();
    private final Set<UUID> homePending = new HashSet<>();
    private record Invite(Nation nation, long expires) {}
    public GuildCommand(S16Countries plugin) { this.plugin=plugin; }

    private void send(CommandSender who, String key, Object... values) { plugin.msg(who,plugin.config().message(key,values)); }
    private Nation mine(Player p) { return plugin.store().of(p.getUniqueId()); }
    private boolean requireGuild(Player p, Nation n) { if(n==null) {send(p,"not-in-guild");return false;}return true; }
    private boolean allow(Player p, Nation n, String permission) {
        if(!requireGuild(p,n))return false;
        if(n.hasPermission(p.getUniqueId(),permission,plugin.config()))return true;
        send(p,"no-permission");return false;
    }
    private boolean worldEnabled(World world) {
        return plugin.getConfig().getStringList("general.enabled-worlds").contains(world.getName()) ||
                (plugin.config().bool("general.allow-nether-end") && world.getEnvironment()!=World.Environment.NORMAL);
    }
    private boolean afford(Player p, int cost) {
        if (cost<=0) return true;
        if(!plugin.currency().isSet()) {send(p,"missing-currency");return false;}
        int have=plugin.currency().count(p);
        if(have<cost) {send(p,"not-enough-currency","cost",cost,"have",have);return false;}
        return true;
    }
    private int claimCost(int current) {
        long amount=(long)Math.max(0,plugin.config().integer("claims.base-cost"))
                +(long)current*Math.max(0,plugin.config().integer("claims.per-owned-chunk"));
        return (int)Math.min(Integer.MAX_VALUE,amount);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(sub.equals("admin")) {admin(sender,args);return true;}
        if(!sender.hasPermission("s16countries.use")) {send(sender,"no-permission");return true;}
        if(sub.equals("pomoc") || sub.equals("help")) {help(sender);return true;}
        if(sub.equals("lista")) {
            String names=plugin.store().all().stream().map(n->n.name+" (§e"+n.members.size()+"§7 graczy)").collect(Collectors.joining("§7, "));
            plugin.msg(sender,"§6Państwa (§e"+plugin.store().all().size()+"§6): §7"+(names.isEmpty()?"brak":names));return true;
        }
        if(sub.equals("info") && args.length>=2) {info(sender,plugin.store().byName(args[1]));return true;}
        if(sub.equals("wojny")) {wars(sender);return true;}
        if(sub.equals("ranking") || sub.equals("top")) { ranking(sender);return true; }
        if(!(sender instanceof Player p)) {send(sender,"players-only");return true;}
        Nation n=mine(p);
        switch(sub) {
            case "menu", "gui" -> plugin.menus().guild(p);
            case "zaloz", "stworz", "utworz" -> create(p,args);
            case "usun" -> disband(p,args,n);
            case "info" -> info(p,n);
            case "czlonkowie" -> members(p,n);
            case "zapros" -> invite(p,args,n);
            case "dolacz" -> join(p,args,n);
            case "opusc" -> leave(p,n);
            case "wyrzuc" -> kick(p,args,n);
            case "rola" -> role(p,args,n);
            case "przekaz" -> transfer(p,args,n);
            case "claim", "zajmij" -> claim(p,n);
            case "unclaim", "oddaj" -> unclaim(p,n);
            case "map", "mapa" -> map(p);
            case "pvp" -> pvp(p,n);
            case "sojusz" -> alliance(p,args,n);
            case "zerwijsojusz" -> endAlliance(p,args,n);
            case "sojusze" -> alliances(p,n);
            case "teren" -> territory(p);
            case "wojna" -> war(p,args,n);
            case "pokoj" -> peace(p,args,n);
            case "podbij" -> plugin.wars().startCapture(p);
            case "setdom", "ustawdom" -> setHome(p,n);
            case "dom", "home" -> home(p,n);
            default -> help(p);
        }
        return true;
    }
    private void help(CommandSender p) {
        plugin.msg(p,"§6§lS16Countries §7— komendy (/gildia):");
        for(String line:List.of(
                "§e/zaloz <nazwa> §7— załóż państwo", "§e/menu §7— GUI państwa", "§e/info [państwo], /lista, /czlonkowie",
                "§e/zapros <gracz>, /dolacz <państwo>, /opusc", "§e/wyrzuc <gracz>, /rola <gracz> <rola>, /przekaz <gracz>",
                "§e/claim, /unclaim, /mapa §7— tereny", "§e/pvp §7— PvP między członkami własnej gildii",
                "§e/wojna <państwo>, /wojny, /pokoj <państwo>, /podbij",
                "§e/sojusz <państwo>, /zerwijsojusz <państwo>, /sojusze",
                "§e/teren, /ranking §7— informacja o chunku i top państw",
                "§e/ustawdom, /dom, /usun potwierdz", "§c/admin waluta, /admin reload, /admin pomoc")) plugin.msg(p,line);
    }
    private void create(Player p, String[] args) {
        if(mine(p)!=null) {send(p,"already-in-guild");return;}
        if(args.length<2) {plugin.msg(p,"§eUżycie: /gildia zaloz <nazwa>");return;}
        String name=args[1];
        try {if(!name.matches(plugin.getConfig().getString("general.name-pattern","[A-Za-z0-9_]{3,16}"))) {send(p,"invalid-name");return;}}
        catch(PatternSyntaxException e) {plugin.msg(p,"§cNiepoprawna reguła nazwy w config.yml");return;}
        if(plugin.store().byName(name)!=null) {send(p,"guild-exists");return;}
        if(plugin.store().all().size()>=plugin.config().integer("general.max-guilds")) {plugin.msg(p,"§cLimit gildii na serwerze.");return;}
        if(!worldEnabled(p.getWorld())) {send(p,"world-disabled");return;}
        ClaimKey first=ClaimKey.of(p.getLocation());
        if(plugin.config().integer("claims.max-per-guild")<1 && plugin.config().bool("claims.auto-claim-on-create")) {
            send(p,"claim-limit");return;
        }
        if(plugin.regions().blocks(first)) {send(p,"region-no-claim");return;}
        if(plugin.config().bool("claims.auto-claim-on-create") && plugin.store().at(first)!=null) {send(p,"claim-occupied");return;}
        int cost=Math.max(0,plugin.config().integer("currency.create-cost"));
        if(!afford(p,cost))return;
        if(!plugin.currency().pay(p,cost)) {plugin.msg(p,"§cBłąd płatności.");return;}
        Nation n=new Nation(name,p.getUniqueId(),plugin.config().roles().getString("settings.leader-role","prezydent"),plugin.config().bool("pvp.friendly-fire-default"));
        plugin.store().put(n);
        if(plugin.config().bool("claims.auto-claim-on-create")) plugin.store().claim(n,first);
        plugin.msg(p,plugin.config().message("created","guild",name));
        if(plugin.config().bool("general.broadcast-create")) plugin.broadcastRaw("§6[S16] §aPowstało państwo §e"+name+"§a!");
    }
    private void disband(Player p,String[] args,Nation n) {
        if(!allow(p,n,"DISBAND"))return;
        if(!p.getUniqueId().equals(n.leader)) {plugin.msg(p,"§cTylko prezydent może usunąć państwo.");return;}
        if(args.length<2 || !args[1].equalsIgnoreCase("potwierdz") || disbandConfirm.getOrDefault(p.getUniqueId(),0L)<System.currentTimeMillis()) {
            disbandConfirm.put(p.getUniqueId(),System.currentTimeMillis()+30_000L);
            plugin.msg(p,"§cUWAGA: wszystkie chunki zostaną zwolnione! Potwierdź w ciągu 30 s: §e/gildia usun potwierdz");return;
        }
        plugin.store().delete(n);
        disbandConfirm.remove(p.getUniqueId());
        plugin.msg(p,"§aUsunięto gildię §e"+n.name);
        if(plugin.config().bool("general.broadcast-disband")) plugin.broadcastRaw("§6[S16] §ePaństwo §c"+n.name+" §ezostało rozwiązane.");
    }
    private void info(CommandSender p,Nation n) {
        if(n==null) {send(p,"guild-not-found");return;}
        plugin.msg(p,"§6§l"+n.name+" §8| §7Lider: §e"+playerName(n.leader));
        plugin.msg(p,"§7Członkowie: §e"+n.members.size()+" §8| §7Chunki: §e"+n.claims.size()+" §8| §7PvP sojuszników: §e"+(n.friendlyFire?"tak":"nie"));
        plugin.msg(p,"§7Wojny: §e"+plugin.wars().involving(n));
    }
    private String playerName(UUID id) {String name=Bukkit.getOfflinePlayer(id).getName();return name==null?id.toString().substring(0,8):name;}
    private void members(Player p,Nation n) {
        if(!requireGuild(p,n))return;
        plugin.msg(p,"§6Członkowie §e"+n.name+"§6:");
        for(var entry:n.members.entrySet()) {
            String role=plugin.config().roles().getString("roles."+entry.getValue()+".name",entry.getValue());
            plugin.msg(p,"§7- §e"+playerName(entry.getKey())+" §8— "+plugin.config().text(role));
        }
    }
    private Player online(Player p,String[] args,String usage) {
        if(args.length<2) {plugin.msg(p,"§eUżycie: "+usage);return null;}
        Player other=Bukkit.getPlayerExact(args[1]);
        if(other==null) plugin.msg(p,"§cGracz musi być online.");
        return other;
    }
    private void invite(Player p,String[] args,Nation n) {
        if(!allow(p,n,"INVITE"))return;
        Player other=online(p,args,"/gildia zapros <gracz>");if(other==null)return;
        if(mine(other)!=null) {plugin.msg(p,"§cGracz jest już w gildii.");return;}
        if(n.members.size()>=plugin.config().integer("general.max-members")) {plugin.msg(p,"§cLimit członków.");return;}
        long expiration=System.currentTimeMillis()+plugin.config().seconds("general.invite-expire-seconds")*1000;
        invites.put(other.getUniqueId(),new Invite(n,expiration));
        plugin.msg(p,"§aZaproszono §e"+other.getName());
        plugin.msg(other,"§aOtrzymano zaproszenie do §e"+n.name+"§a: §e/gildia dolacz "+n.name);
    }
    private void join(Player p,String[] args,Nation n) {
        if(n!=null) {send(p,"already-in-guild");return;}
        if(args.length<2) {plugin.msg(p,"§eUżycie: /gildia dolacz <państwo>");return;}
        Nation target=plugin.store().byName(args[1]);
        if(target==null) {send(p,"guild-not-found");return;}
        Invite inv=invites.get(p.getUniqueId());
        if(inv==null || inv.nation!=target || inv.expires<System.currentTimeMillis()) {plugin.msg(p,"§cNie masz ważnego zaproszenia.");return;}
        if(target.members.size()>=plugin.config().integer("general.max-members")) {plugin.msg(p,"§cLimit członków.");return;}
        plugin.store().addMember(target,p.getUniqueId(),plugin.config().roles().getString("settings.default-role","obywatel"));
        invites.remove(p.getUniqueId());
        plugin.msg(p,"§aDołączyłeś do §e"+target.name);
    }
    private void leave(Player p,Nation n) {
        if(!requireGuild(p,n))return;
        if(p.getUniqueId().equals(n.leader)) {plugin.msg(p,"§cNajpierw przekaż przywództwo: /gildia przekaz <gracz>");return;}
        plugin.store().removeMember(n,p.getUniqueId());
        plugin.msg(p,"§aOpuściłeś państwo §e"+n.name);
    }
    private UUID memberId(Nation n,String name) {
        for(UUID id:n.members.keySet()) if(playerName(id).equalsIgnoreCase(name)) return id;
        return null;
    }
    private void kick(Player p,String[] args,Nation n) {
        if(!allow(p,n,"KICK"))return;
        if(args.length<2) {plugin.msg(p,"§e/gildia wyrzuc <gracz>");return;}
        UUID id=memberId(n,args[1]);
        if(id==null) {plugin.msg(p,"§cGracz nie jest w gildii.");return;}
        if(id.equals(n.leader)) {plugin.msg(p,"§cNie możesz wyrzucić prezydenta.");return;}
        if(id.equals(p.getUniqueId())) {plugin.msg(p,"§cDo odejścia służy /gildia opusc.");return;}
        plugin.store().removeMember(n,id);
        plugin.msg(p,"§aUsunięto członka §e"+playerName(id));
        Player online=Bukkit.getPlayer(id);if(online!=null)plugin.msg(online,"§cUsunięto Cię z gildii §e"+n.name);
    }
    private void role(Player p,String[] args,Nation n) {
        if(!allow(p,n,"ROLE"))return;
        if(args.length<3) {plugin.msg(p,"§e/gildia rola <gracz> <rola>");return;}
        UUID id=memberId(n,args[1]);
        if(id==null) {plugin.msg(p,"§cNie ma takiego członka.");return;}
        if(id.equals(n.leader)) {plugin.msg(p,"§cNie możesz zmienić roli prezydenta.");return;}
        String role=args[2].toLowerCase(Locale.ROOT);
        if(!role.matches("[a-z0-9_-]+") || !plugin.config().roles().contains("roles."+role) || role.equalsIgnoreCase(plugin.config().roles().getString("settings.protected-role","prezydent"))) {
            plugin.msg(p,"§cNieznana lub chroniona rola. Zobacz roles.yml.");return;
        }
        n.members.put(id,role);
        plugin.store().save();
        plugin.msg(p,"§aRola §e"+playerName(id)+" §a→ §e"+role);
    }
    private void transfer(Player p,String[] args,Nation n) {
        if(!allow(p,n,"TRANSFER"))return;
        if(!p.getUniqueId().equals(n.leader)) {plugin.msg(p,"§cTylko obecny prezydent może przekazać gildie.");return;}
        if(args.length<2) {plugin.msg(p,"§e/gildia przekaz <członek>");return;}
        UUID id=memberId(n,args[1]);if(id==null||id.equals(n.leader)) {plugin.msg(p,"§cWybierz innego członka.");return;}
        n.members.put(n.leader,plugin.config().roles().getString("settings.ex-leader-role","zastepca"));
        n.leader=id;
        n.members.put(id,plugin.config().roles().getString("settings.leader-role","prezydent"));
        plugin.store().save();
        plugin.msg(p,"§aNowym przywódcą państwa jest §e"+playerName(id));
    }
    private void claim(Player p,Nation n) {
        if(!allow(p,n,"CLAIM"))return;
        if(!worldEnabled(p.getWorld())) {send(p,"world-disabled");return;}
        ClaimKey key=ClaimKey.of(p.getLocation());
        if(plugin.regions().blocks(key)) {send(p,"region-no-claim");return;}
        if(plugin.store().at(key)!=null) {send(p,"claim-occupied");return;}
        if(n.claims.size()>=plugin.config().integer("claims.max-per-guild")) {send(p,"claim-limit");return;}
        if(plugin.config().bool("claims.must-touch-own-claim") && !n.claims.isEmpty() && !n.touches(key)) {send(p,"claim-must-touch");return;}
        int cost=claimCost(n.claims.size());
        if(!afford(p,cost))return;
        if(!plugin.currency().pay(p,cost)) {plugin.msg(p,"§cBłąd płatności.");return;}
        plugin.store().claim(n,key);
        send(p,"claimed","x",key.x(),"z",key.z());
    }
    private void unclaim(Player p,Nation n) {
        if(!allow(p,n,"UNCLAIM"))return;
        ClaimKey key=ClaimKey.of(p.getLocation());
        if(plugin.store().at(key)!=n) {send(p,"not-your-claim");return;}
        if(!n.remainsConnectedWithout(key)) {send(p,"claim-disconnect");return;}
        plugin.store().unclaim(n,key);
        if(n.home!=null&&ClaimKey.of(n.home).equals(key)) {n.home=null;plugin.store().save();}
        plugin.currency().refund(p,Math.max(0,plugin.config().integer("claims.unclaim-refund")));
        send(p,"unclaimed","x",key.x(),"z",key.z());
    }
    private void map(Player p) {
        ClaimKey here=ClaimKey.of(p.getLocation());
        Nation own=mine(p);
        int radius=Math.max(1,Math.min(10,plugin.config().integer("claims.map-radius")));
        plugin.msg(p,"§6Mapa chunków §7(§aTwoje §cCudze §8Wolne §eTy§7) §8X="+here.x()+" Z="+here.z());
        for(int dz=-radius;dz<=radius;dz++) {
            StringBuilder row=new StringBuilder();
            for(int dx=-radius;dx<=radius;dx++) {
                ClaimKey key=here.offset(dx,dz);
                Nation at=plugin.store().at(key);
                if(dx==0&&dz==0) row.append("§e◆");
                else row.append(at==null?"§8■":at==own?"§a■":"§c■");
            }
            plugin.msg(p,row.toString());
        }
    }
    private void territory(Player p) {
        ClaimKey key=ClaimKey.of(p.getLocation());
        Nation n=plugin.store().at(key);
        plugin.msg(p,"§eChunk §6"+key.x()+", "+key.z()+" §7— "+(n==null?"§aDzicz":"§c"+n.name));
    }
    private void ranking(CommandSender sender) {
        plugin.msg(sender,"§6§lRanking państw §7(według liczby chunków):");
        List<Nation> sorted=plugin.store().all().stream().sorted(Comparator.comparingInt((Nation n) -> n.claims.size()).reversed()).limit(10).toList();
        for(int i=0;i<sorted.size();i++) {
            Nation n=sorted.get(i);
            plugin.msg(sender,"§e"+(i+1)+". §6"+n.name+" §7— §e"+n.claims.size()+" §7chunków, §e"+n.members.size()+" §7graczy");
        }
        if(sorted.isEmpty())plugin.msg(sender,"§7Brak państw.");
    }
    private void alliances(Player p,Nation n) {
        if(!requireGuild(p,n))return;
        plugin.msg(p,"§6Sojusze §e"+n.name+"§6: §7"+(n.allies.isEmpty()?"brak":String.join(", ",n.allies)));
    }
    private void alliance(Player p,String[] args,Nation n) {
        if(!allow(p,n,"DIPLOMACY"))return;
        if(!plugin.config().bool("diplomacy.enabled")) {plugin.msg(p,"§cSojusze wyłączone.");return;}
        if(args.length<2) {plugin.msg(p,"§e/gildia sojusz <państwo>");return;}
        Nation target=plugin.store().byName(args[1]);
        if(target==null){send(p,"guild-not-found");return;}
        if(n==target){plugin.msg(p,"§cNie można zawrzeć sojuszu z samym sobą.");return;}
        if(n.allies.contains(target.name.toLowerCase(Locale.ROOT))){plugin.msg(p,"§cJesteście już sojusznikami.");return;}
        int cap=plugin.config().integer("diplomacy.max-alliances");
        if(n.allies.size()>=cap || target.allies.size()>=cap) {plugin.msg(p,"§cLimit sojuszy.");return;}
        if(plugin.wars().between(n,target)!=null){plugin.msg(p,"§cNie można zawrzeć sojuszu podczas wojny.");return;}
        String incoming=target.name.toLowerCase(Locale.ROOT)+":"+n.name.toLowerCase(Locale.ROOT);
        if(allianceOffers.getOrDefault(incoming,0L)>System.currentTimeMillis()) {
            n.allies.add(target.name.toLowerCase(Locale.ROOT));
            target.allies.add(n.name.toLowerCase(Locale.ROOT));
            allianceOffers.remove(incoming);
            plugin.store().save();
            plugin.broadcastRaw("§a[S16 Sojusz] §e"+n.name+" §ai §e"+target.name+" §asą teraz sojusznikami!");
        } else {
            String outgoing=n.name.toLowerCase(Locale.ROOT)+":"+target.name.toLowerCase(Locale.ROOT);
            allianceOffers.put(outgoing,System.currentTimeMillis()+plugin.config().seconds("diplomacy.proposal-expire-seconds")*1000L);
            plugin.msg(p,"§aWysłano propozycję sojuszu §e"+target.name+"§a. Druga strona: §e/gildia sojusz "+n.name);
        }
    }
    private void endAlliance(Player p,String[] args,Nation n) {
        if(!allow(p,n,"DIPLOMACY"))return;
        if(args.length<2) {plugin.msg(p,"§e/gildia zerwijsojusz <państwo>");return;}
        Nation target=plugin.store().byName(args[1]);
        if(target==null){send(p,"guild-not-found");return;}
        if(!n.allies.remove(target.name.toLowerCase(Locale.ROOT))){plugin.msg(p,"§cBrak sojuszu.");return;}
        target.allies.remove(n.name.toLowerCase(Locale.ROOT));
        plugin.store().save();
        plugin.broadcastRaw("§c[S16] Sojusz §e"+n.name+" §ci §e"+target.name+" §czostał zerwany.");
    }
    private void pvp(Player p,Nation n) {
        if(!allow(p,n,"PVP"))return;
        n.friendlyFire=!n.friendlyFire;
        plugin.store().save();
        send(p,n.friendlyFire?"pvp-on":"pvp-off");
    }
    private void war(Player p,String[] args,Nation n) {
        if(!allow(p,n,"WAR"))return;
        if(!plugin.config().bool("war.enabled")) {plugin.msg(p,"§cWojny są wyłączone.");return;}
        if(args.length<2) {plugin.msg(p,"§e/gildia wojna <państwo>");return;}
        Nation target=plugin.store().byName(args[1]);
        if(target==null) {send(p,"guild-not-found");return;}
        if(target==n) {plugin.msg(p,"§cNie możesz wypowiedzieć wojny samemu sobie.");return;}
        if(!plugin.config().bool("diplomacy.allow-war-between-allies") && n.allies.contains(target.name.toLowerCase(Locale.ROOT))) {
            plugin.msg(p,"§cNajpierw zerwij sojusz z tym państwem.");return;
        }
        if(plugin.wars().between(n,target)!=null) {plugin.msg(p,"§cTa wojna już trwa lub się przygotowuje.");return;}
        int max=plugin.config().integer("war.max-active-wars-per-guild");
        if(plugin.wars().involving(n)>=max||plugin.wars().involving(target)>=max) {plugin.msg(p,"§cJedno z państw osiągnęło limit wojen.");return;}
        long cooldown=plugin.config().seconds("war.war-declare-cooldown-seconds")*1000;
        if(System.currentTimeMillis()<n.lastWarEnded+cooldown) {plugin.msg(p,"§cOdczekaj przed wypowiedzeniem kolejnej wojny.");return;}
        int cost=Math.max(0,plugin.config().integer("currency.war-declare-cost"));
        if(!afford(p,cost))return;
        if(!plugin.currency().pay(p,cost)) {plugin.msg(p,"§cBłąd płatności.");return;}
        long prep=plugin.config().seconds("war.preparation-seconds");
        plugin.wars().declare(n,target,prep,Math.max(1,plugin.config().seconds("war.duration-seconds")));
        send(p,"war-declared","guild",target.name,"seconds",prep);
        plugin.broadcastRaw("§c[S16 Wojna] §e"+n.name+" §cwypowiedziało wojnę §e"+target.name+"§c. Początek za §e"+prep+"s§c.");
    }
    private void wars(CommandSender p) {
        plugin.msg(p,"§c§lWojny państw:");
        if(plugin.store().wars.isEmpty())plugin.msg(p,"§7Brak aktualnych wojen.");
        for(War w:plugin.store().wars) {
            long secs=Math.max(0,(w.active()?w.end-System.currentTimeMillis():w.start-System.currentTimeMillis())/1000);
            plugin.msg(p,"§e"+w.a.name+" §7vs §e"+w.b.name+" §8| "+(w.active()?"§cAKTYWNA":"§6PRZYGOTOWANIE")+" §7— "+secs+"s");
        }
    }
    private void peace(Player p,String[] args,Nation n) {
        if(!allow(p,n,"PEACE"))return;
        if(args.length<2) {plugin.msg(p,"§e/gildia pokoj <państwo>");return;}
        Nation other=plugin.store().byName(args[1]);
        War w=plugin.wars().between(n,other);
        if(w==null) {plugin.msg(p,"§cNie ma takiej wojny.");return;}
        if(n==w.a) w.peaceA=true;else w.peaceB=true;
        if(w.peaceA&&w.peaceB) {plugin.wars().stop(w);send(p,"peace-accepted");}
        else {plugin.store().save();send(p,"peace-proposed","guild",n.name);}
    }
    private void setHome(Player p,Nation n) {
        if(!allow(p,n,"SETHOME"))return;
        if(!plugin.config().bool("home.enabled")) {plugin.msg(p,"§cSiedziby są wyłączone.");return;}
        if(plugin.config().bool("home.require-owned-chunk")&&plugin.store().at(p.getLocation())!=n) {send(p,"not-your-claim");return;}
        n.home=p.getLocation();
        plugin.store().save();
        send(p,"home-set");
    }
    private void home(Player p,Nation n) {
        if(!requireGuild(p,n))return;
        if(!plugin.config().bool("home.enabled")) {plugin.msg(p,"§cSiedziby są wyłączone.");return;}
        if(n.home==null || n.home.getWorld()==null) {plugin.msg(p,"§cGildia nie ustawiła siedziby.");return;}
        long now=System.currentTimeMillis(), cooldown=plugin.config().seconds("home.cooldown-seconds")*1000;
        if(homePending.contains(p.getUniqueId())) {plugin.msg(p,"§cTeleportacja już trwa.");return;}
        if(homeCooldown.getOrDefault(p.getUniqueId(),0L)>now) {plugin.msg(p,"§cTeleportacja dostępna za "+((homeCooldown.get(p.getUniqueId())-now+999)/1000)+"s.");return;}
        long seconds=plugin.config().seconds("home.teleport-delay-seconds");
        Location start=p.getLocation().clone();
        Location target=n.home.clone();
        send(p,"home-teleport","seconds",seconds);
        homePending.add(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(!p.isOnline() || p.isDead() || !p.getWorld().equals(start.getWorld()) || p.getLocation().distanceSquared(start)>0.25 || plugin.store().of(p.getUniqueId())!=n) {
                homePending.remove(p.getUniqueId());
                if(p.isOnline())send(p,"home-cancel");return;
            }
            p.teleportAsync(target).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                homePending.remove(p.getUniqueId());
                if(ok && p.isOnline()) {homeCooldown.put(p.getUniqueId(),System.currentTimeMillis()+cooldown);plugin.msg(p,plugin.config().message("home-teleported"));}
            }));
        },seconds*20L);
    }
    private void admin(CommandSender sender,String[] args) {
        if(!sender.hasPermission("s16countries.admin")) {send(sender,"no-permission");return;}
        String action=args.length<2?"pomoc":args[1].toLowerCase(Locale.ROOT);
        if(action.equals("pomoc")) {
            plugin.msg(sender,"§e/gildia admin waluta §7— ustaw item przez GUI");
            plugin.msg(sender,"§e/gildia admin resetwaluta §7— usuń walutę");
            plugin.msg(sender,"§e/gildia admin reload §7— wczytaj config, role, GUI, walutę");
            plugin.msg(sender,"§e/gildia admin claim <gildia> §7— zajmij chunk dla gildii");
            plugin.msg(sender,"§e/gildia admin unclaim §7— usuń claim pod nogami");
            plugin.msg(sender,"§e/gildia admin wojna <A> <B> §7— natychmiastowa wojna");
            plugin.msg(sender,"§e/gildia admin region pomoc §7— regiony bez claimów");
            plugin.msg(sender,"§e/gildia admin stopwojna <A> <B> §7— zakończ wojnę");return;
        }
        if(action.equals("reload")) {plugin.reloadAll();send(sender,"admin-reloaded");return;}
        if(action.equals("region")) {plugin.regionCommands().execute(sender, args);return;}
        if(action.equals("resetwaluta")) {plugin.currency().set(null);plugin.msg(sender,"§aUsunięto wzorzec waluty.");return;}
        if(action.equals("waluta")) {
            if(sender instanceof Player p)plugin.menus().currency(p);
            else send(sender,"players-only");return;
        }
        if(action.equals("claim")||action.equals("unclaim")) {
            if(!(sender instanceof Player p)) {send(sender,"players-only");return;}
            ClaimKey key=ClaimKey.of(p.getLocation());
            if(action.equals("unclaim")) {
                Nation prev=plugin.store().at(key);
                if(prev==null) {plugin.msg(p,"§cChunk jest wolny.");return;}
                plugin.store().unclaim(prev,key);
                if(prev.home!=null&&key.equals(ClaimKey.of(prev.home))) {prev.home=null;plugin.store().save();}
                plugin.msg(p,"§aUsunięto claim.");return;
            }
            if(args.length<3) {plugin.msg(p,"§e/gildia admin claim <gildia>");return;}
            Nation target=plugin.store().byName(args[2]);
            if(target==null) {send(p,"guild-not-found");return;}
            if(plugin.regions().blocks(key)) {send(p,"region-no-claim");return;}
            if(plugin.store().at(key)==target) {plugin.msg(p,"§cChunk już należy do tej gildii.");return;}
            plugin.store().claim(target,key);
            plugin.msg(p,"§aPrzypisano chunk do §e"+target.name);return;
        }
        if(action.equals("wojna")||action.equals("stopwojna")) {
            if(args.length<4) {plugin.msg(sender,"§e/gildia admin "+action+" <A> <B>");return;}
            Nation a=plugin.store().byName(args[2]),b=plugin.store().byName(args[3]);
            if(a==null||b==null||a==b) {plugin.msg(sender,"§cNiepoprawne gildie.");return;}
            War war=plugin.wars().between(a,b);
            if(action.equals("stopwojna")) {
                if(war==null) {plugin.msg(sender,"§cBrak wojny.");return;}
                plugin.wars().stop(war);
            } else {
                if(war!=null) {plugin.msg(sender,"§cTa wojna już istnieje.");return;}
                plugin.wars().declare(a,b,0L,Math.max(1,plugin.config().seconds("war.duration-seconds")));
                plugin.broadcast("war-start","a",a.name,"b",b.name);
            }
            plugin.msg(sender,"§aWykonano polecenie.");return;
        }
        plugin.msg(sender,"§cNieznana komenda admina.");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args) {
        if(args.length==1) {
            List<String> items=new ArrayList<>(List.of("pomoc","menu","zaloz","usun","info","lista","czlonkowie","zapros","dolacz","opusc","wyrzuc","rola","przekaz","claim","unclaim","mapa","teren","ranking","pvp","sojusz","sojusze","zerwijsojusz","wojna","wojny","pokoj","podbij","ustawdom","dom"));
            if(sender.hasPermission("s16countries.admin"))items.add("admin");
            return items.stream().filter(item->item.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if(args.length==2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("s16countries.admin"))
            return List.of("pomoc","waluta","resetwaluta","reload","claim","unclaim","wojna","stopwojna","region").stream().filter(s->s.startsWith(args[1])).toList();
        if(args.length==3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("region"))
            return List.of("pomoc","narzedzie","zaznacz-chunki","zaznacz-bloki","promien","pokaz","lista","usun").stream().filter(v -> v.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        if(args.length==2 && List.of("wojna","pokoj","dolacz","info","sojusz","zerwijsojusz").contains(args[0].toLowerCase(Locale.ROOT)))
            return plugin.store().all().stream().map(n->n.name).filter(s->s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if(args.length==3 && args[0].equalsIgnoreCase("rola")) {
            var roles=plugin.config().roles().getConfigurationSection("roles");
            return roles==null?List.of():roles.getKeys(false).stream().filter(s->s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if(args.length==2 && List.of("zapros","wyrzuc","rola","przekaz").contains(args[0].toLowerCase(Locale.ROOT)))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s->s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
