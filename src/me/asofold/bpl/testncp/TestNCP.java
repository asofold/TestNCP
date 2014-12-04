package me.asofold.bpl.testncp;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.access.IViolationInfo;
import fr.neatmonster.nocheatplus.hooks.APIUtils;
import fr.neatmonster.nocheatplus.hooks.IFirst;
import fr.neatmonster.nocheatplus.hooks.IStats;
import fr.neatmonster.nocheatplus.hooks.NCPHook;
import fr.neatmonster.nocheatplus.hooks.NCPHookManager;
import fr.neatmonster.nocheatplus.logging.LogManager;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.utilities.TickTask;

public class TestNCP extends JavaPlugin implements NCPHook, IStats, IFirst, Listener{

    public static final class NoViolationHook implements NCPHook {
        protected final Set<String> players = new HashSet<String>();
        @Override
        public boolean onCheckFailure(final CheckType type, final Player player, final IViolationInfo info) {
            return players.contains(player.getName());
        }

        @Override
        public String getHookVersion() {
            return "1.0";
        }

        @Override
        public String getHookName() {
            return "NoViolation(TestNCP)";
        }
        public void addPlayer(String playerName){
            players.add(playerName);
        }
        public void removePlayer(String playerName){
            players.remove(playerName);
        }
        public boolean hasPlayers(){
            return !players.isEmpty();
        }
        /**
         * 
         * @param playerName
         * @return True, if player is newly added, false if removed.
         */
        public boolean togglePlayer(final String playerName){
            if (players.remove(playerName)){
                return false;
            }
            else{
                players.add(playerName);
                return true;
            }
        }
    };

    /**
     * Lower case names.
     */
    protected final Set<String> testers = new LinkedHashSet<String>();

    /**
     * Online testers.
     */
    protected final Set<Player> activeReceivers = new LinkedHashSet<Player>();

    /**
     * Tester -> Set of names to receive messages for.
     */
    protected final Map<String, Set<String>> inputs = new HashMap<String, Set<String>>();

    protected boolean toConsole = false;
    
    protected boolean toTraceFile = true;

    protected boolean details = true;

    protected boolean testAll = false;

    protected final Set<ParameterName> detailsUsed = new LinkedHashSet<ParameterName>();

    protected final DecimalFormat format = new DecimalFormat("#.###");

    protected final long[] lagTicks = new long[]{1, 5, 10, 20, 1200};

    protected final NoViolationHook noViolationHook = new  NoViolationHook();

    public TestNCP(){
        for (final ParameterName param : ParameterName.values()){
            if (param == ParameterName.IP || param == ParameterName.PLAYER || param == ParameterName.VIOLATIONS || param == ParameterName.CHECK) continue;
            else detailsUsed.add(param);
        }
        DecimalFormatSymbols sym = format.getDecimalFormatSymbols();
        sym.setDecimalSeparator('.');
        format.setMaximumFractionDigits(3);
        format.setDecimalSeparatorAlwaysShown(false);
        format.setMinimumIntegerDigits(1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
            String label, String[] args) {
        String cmd = "";
        if (args.length > 0) cmd = args[0].trim().toLowerCase();
        if (cmd.equals("reload")){
            if (!checkPerm(sender, "testncp.admin.reload")) return true;
            reloadSettings();
            sender.sendMessage("[TestNCP] Settings reloaded.");
            return true;
        }
        else if (cmd.equals("move") && args.length > 1){
            if (!checkPerm(sender, "testncp.admin.fake.move")) return true; 
            if (!expectPlayer(sender)) return true;
            sender.sendMessage("[TestNCP] Feature not available.");
            //    		fakeMove((Player) sender, args, 1, sender);
            return true; 
        }
        else if (cmd.equals("input")){
            if (!expectPlayer(sender)) return true;
            String lcName = sender.getName().toLowerCase();
            if (args.length == 1 || args.length == 2 && args[1].equals("*")){
                inputs.remove(lcName);
                sender.sendMessage("[TestNCP] Cleared inputs list (receive all).");
            }
            else{
                Set<String> names = inputs.get(lcName);
                if (names == null){
                    names = new HashSet<String>();
                    inputs.put(lcName, names);
                }
                for (int i = 1; i < args.length; i++){
                    names.add(args[i].trim().toLowerCase());
                }
                sender.sendMessage("[TestNCP] Added names to inputs.");
                sendInputs((Player) sender);
            }
            return true;
        }
        else if (cmd.matches("velocity|vel")){
            if (!expectPlayer(sender) || args.length != 2) return false;
            if (!checkPerm(sender, "testncp.cmd.velocity")) return true;
            double x = Double.NaN;
            try{
                x = Double.parseDouble(args[1]);
            }
            catch(NumberFormatException e){}
            if (Double.isNaN(x)) {
                sender.sendMessage(ChatColor.DARK_RED + "Bad number: " + args[1]);
                return false;
            } else if (x < -1000.0 || x > 1000.0) {
                sender.sendMessage(ChatColor.DARK_RED + "Number out of range (+- 1000.0): " + args[1]);
                return false;
            }
            Player player = (Player) sender;
            player.setVelocity(player.getLocation().getDirection().normalize().multiply(x));
            return true;
        }
        else if (cmd.matches("noviolations|noviolation|noviol")){
            if (!expectPlayer(sender) || args.length != 1) return false;
            if (!checkPerm(sender, "testncp.cmd.noviolations")) return true;
            final boolean has = noViolationHook.hasPlayers();
            if (noViolationHook.togglePlayer(sender.getName())){
                // Added
                if (!has){
                    NCPHookManager.addHook(CheckType.ALL, noViolationHook);
                }
                sender.sendMessage(ChatColor.GREEN + "You generate no further violations (actions actually).");
            }
            else{
                if (!noViolationHook.hasPlayers()){
                    NCPHookManager.removeHook(noViolationHook);
                }
                sender.sendMessage(ChatColor.RED + "You can now generate violations (actions actually) again, might use: /ncp remove " + sender.getName());
            }
            return true;
        }
        return false;
    }

    public static boolean checkPerm(CommandSender sender, String perm){
        if (sender.hasPermission(perm)) return true;
        sender.sendMessage(ChatColor.DARK_RED + "You don't have permission (" + perm + ").");
        return false;
    }

    public static boolean expectPlayer(CommandSender sender){
        if (sender instanceof Player) return true;
        sender.sendMessage("Only players can do this.");
        return false;
    }

    @Override
    public void onDisable() {
        NCPHookManager.removeHook(this);
        System.out.println(getDescription().getFullName() + " is disabled.");
    }

    @Override
    public void onEnable() {
        reloadSettings();
        NCPHookManager.addHook(CheckType.ALL, this);
        getServer().getPluginManager().registerEvents(this, this);
        System.out.println(getDescription().getFullName() + " is enabled.");
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event){
        final Player player = event.getPlayer();
        activeReceivers.remove(player);
        final String lcName = player.getName().trim().toLowerCase();
        if (testAll || testers.contains(lcName)){
            sendInputs(player);
            activeReceivers.add(player);
        }
        else{
            inputs.remove(lcName);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event){
        onLeave(event.getPlayer());
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event){
        onLeave(event.getPlayer());
    }

    private void onLeave(Player player) {
        activeReceivers.remove(player);
        noViolationHook.removePlayer(player.getName()); // Safety thing.
        if (!noViolationHook.hasPlayers()){
            NCPHookManager.removeHook(noViolationHook);
        }
    }

    public void sendInputs(Player player) {
        Set<String> names = inputs.get(player.getName().trim().toLowerCase());
        if (names != null){
            StringBuilder b  = new StringBuilder(256);
            b.append("[TestNCP] Your inputs:");
            for (String n : names){
                b.append(" ");
                b.append(n);
            }
            player.sendMessage(b.toString());
        }
    }

    public void reloadSettings() {
        this.testers.clear();
        MemoryConfiguration defaults = new MemoryConfiguration();
        defaults.set("testers", new LinkedList<String>());
        defaults.set("logging.console", false);
        defaults.set("logging.tracefile", true);
        defaults.set("logging.details", true);
        reloadConfig();
        Configuration config = getConfig();
        config.setDefaults(defaults);
        config.options().copyDefaults(true);
        saveConfig();
        toConsole = config.getBoolean("logging.console");
        toTraceFile = config.getBoolean("logging.tracefile");
        details = config.getBoolean("logging.details");
        List<String> testers = config.getStringList("testers");
        for (String n : testers){
            this.testers.add(n.trim().toLowerCase());
        }
        testAll = this.testers.contains("*");
        updateActiveTesters();
    }

    /**
     * Re-evaluate all online players according to settings.
     */
    public void updateActiveTesters() {
        activeReceivers.clear();
        for (final Player player : getServer().getOnlinePlayers()){
            if (testAll || testers.contains(player.getName().trim().toLowerCase())){
                activeReceivers.add(player);
            }
        }
    }

    @Override
    public String getHookName() {
        return "TestNCPHook";
    }

    @Override
    public String getHookVersion() {
        return "1.0";
    }

    @Override
    public boolean onCheckFailure(final CheckType checkType, final Player player, final IViolationInfo info) {
        if (!toConsole && activeReceivers.isEmpty()) return false;
        final String name = player.getName().toLowerCase();
        if (!testAll && !testers.contains(name)) return false; // TODO
        if (APIUtils.needsSynchronization(checkType)){
            Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                @Override
                public void run() {
                    log(checkType, player, info);
                }
            });
        }
        else log(checkType, player, info);
        return false;
    }

    public void log(final CheckType checkType, final Player player, final IViolationInfo info) {
        final String name = player.getName();
        final String lcName = name.toLowerCase();
        // Build the message.
        final StringBuilder builder = new StringBuilder(250);
        // Name
        builder.append(ChatColor.YELLOW + "[TestNCP] " + ChatColor.WHITE + name + " ");
        // Check
        builder.append(ChatColor.AQUA + checkType.name());
        // VL
        builder.append(ChatColor.WHITE + " vl " + format.format(info.getTotalVl()) + " (+" + format.format(info.getAddedVl()) + ")");
        // Details
        if (details){
            // Parameters
            if (info.hasParameters()){
                builder.append(ChatColor.GRAY + " Details:");
                final ViolationData data = (ViolationData) info;
                // TODO: Add a way to get the parameter map to NCP ?
                for (final ParameterName param : detailsUsed){
                    final String paramName = param.name();
                    final String val = data.getParameter(param);
                    if (val == null || val.equals("<?" + paramName + ">")) continue;
                    else builder.append(" " + param + "=" + val);
                }
                builder.append(" cancel=" + info.hasCancel());
            }
            // Lag
            final String[] lagSpecs = new String[lagTicks.length];
            boolean hasLag = false;
            for (int i = 0; i < lagTicks.length; i++){
                final long ticks = lagTicks[i];
                final float lag = TickTask.getLag(50L * ticks);
                if (lag > 1.0){
                    hasLag = true;
                    lagSpecs[i] = " " + lagTicks[i] + "@" + format.format(lag);
                }
                else lagSpecs[i] = null;
            }
            if (hasLag){
                builder.append(" Ticks[Lag]:");
                for (int i = 0; i < lagTicks.length; i++){
                    if (lagSpecs[i] != null) builder.append(lagSpecs[i]);
                }
            }
        }
        final String msg = builder.toString();
        // Log the message
        if (toTraceFile || toConsole) {
            final LogManager logManager = NCPAPIProvider.getNoCheatPlusAPI().getLogManager();
            final String noColorMsg = ChatColor.stripColor(msg);
            if (toTraceFile) {
                logManager.debug(Streams.TRACE_FILE, noColorMsg);
            }
            if (toConsole) {
                logManager.info(Streams.PLUGIN_LOGGER, noColorMsg);
            }
        }
        for (final Player ref : activeReceivers){
            final String lcRef = ref.getName().toLowerCase();
            final Set<String> names = inputs.get(lcRef);
            if (names == null || names.contains(lcName)) ref.sendMessage(msg);
        }
    }

}
