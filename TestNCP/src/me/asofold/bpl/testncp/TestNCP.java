package me.asofold.bpl.testncp;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.asofold.bpl.testncp.utils.DoubleDef;

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
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.access.IViolationInfo;
import fr.neatmonster.nocheatplus.hooks.APIUtils;
import fr.neatmonster.nocheatplus.hooks.IFirst;
import fr.neatmonster.nocheatplus.hooks.IStats;
import fr.neatmonster.nocheatplus.hooks.NCPHook;
import fr.neatmonster.nocheatplus.hooks.NCPHookManager;
import fr.neatmonster.nocheatplus.utilities.TickTask;

public class TestNCP extends JavaPlugin implements NCPHook, IStats, IFirst, Listener{
    
	/**
	 * Lower case names.
	 */
    protected final Set<String> testers = new HashSet<String>();
    
    /**
     * Tester -> Set of names to receive messages for.
     */
    protected final Map<String, Set<String>> inputs = new HashMap<String, Set<String>>();
    
    protected boolean toConsole = true;
    
    protected boolean details = true;
    
    protected boolean testAll = false;
    
    protected final Set<ParameterName> detailsUsed = new LinkedHashSet<ParameterName>();
    
    protected final DecimalFormat format = new DecimalFormat("#.###");
    
    protected final long[] lagTicks = new long[]{1, 5, 10, 20, 1200};
    
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
        if (cmd.equals("reload") && sender.hasPermission("testncp.admin.reload")){
            reloadSettings();
            sender.sendMessage("[TestNCP] Settings reloaded.");
            return true;
        }
        else if (cmd.equals("move") && sender.hasPermission("testncp.admin.fake.move") && args.length > 1){
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
        return false;
    }
    
//    /**
//     * Parses DoubleDef values.
//     * @param player
//     * @param args
//     * @param startIndex
//     * @param notify
//     * @return
//     */
//    public boolean fakeMove(Player player, String[] args, int startIndex, CommandSender notify){
//    	Collection<DoubleDef> doubles = parseDoubles(args, 1, notify);
//		if (doubles == null) return false;
//		return fakeMove(player, doubles, notify);
//    }
    
//    /**
//     * 
//     * @param player
//     * @param doubles
//     * @param notify Gets notifications for errors.
//     * @return
//     */
//    public static boolean fakeMove(Player player, Collection<DoubleDef> doubles, CommandSender notify)
//	{
//    	Location loc = player.getLocation();
//    	double x = loc.getX();
//    	double y = loc.getY();
//    	double z = loc.getZ();
//    	boolean ground = true; // TODO: maybe check if player is on ground ?
//    	float pitch = loc.getPitch();
//		float yaw = loc.getYaw();
//		for (DoubleDef arg : doubles){
//			String key = arg.key;
//			if (key.equals("x")) x = arg.apply(loc.getX());
//			else if (key.equals("y")) y = arg.apply(loc.getY());
//			else if (key.equals("z")) z = arg.apply(loc.getZ());
//			else if (key.equals("pitch") || key.equals("p")) pitch = (float) arg.apply(loc.getPitch());
//			else if (key.equals("yaw") || key.equals("ya")) yaw = (float) arg.apply(loc.getYaw());
//			else if (key.equals("ground") || key.equals("g")) ground = arg.value != 0;
//			else if (key.equals("stance") || key.equals("s")); // Treat after other keys.
//			else{
//				if (notify != null) notify.sendMessage("Bad key: " + arg.key);
//				return false;
//			}
//		}
//		// Stance is extra (after y is set), to allow a little variation of order.
//		double stance = y + 1.65D; // TODO: can it be read ?
//		for (DoubleDef arg : doubles){
//			String key = arg.key;
//			if (key.equals("stance") || key.equals("s")) stance = (float) arg.apply(y);
//		}
//		fakeMove(player, x, y, z, pitch, yaw, stance , ground);
//		return true;
//	}

	public static Collection<DoubleDef> parseDoubles(String[] args, int startIndex, CommandSender notify){
    	List<DoubleDef> result = new ArrayList<DoubleDef>(args.length - startIndex);
    	for (int i = startIndex; i < args.length; i++){
    		try{
    			result.add(new DoubleDef(args[i]));
    		}
    		catch (Throwable t){
    			if (notify != null) notify.sendMessage("Bad double def [key(=|+|-)value] : " + args[i]);
    			return null;
    		}
    	}
    	return result;
    }
    
//    public static void fakeMove(Player player, double x, double y, double z, float pitch, float yaw, double stance, boolean ground) {
//		EntityPlayer mcPlayer = ((CraftPlayer) player).getHandle();
//		Packet10Flying packet = new Packet10Flying();
//		packet.x = x;
//		packet.y = y;
//		packet.z = z;
//		packet.hasPos = true;
//		packet.stance = stance;
//		packet.hasLook = true;
//		packet.yaw = yaw;
//		packet.pitch = pitch;
//		packet.g = ground; // Assumption !
//		mcPlayer.netServerHandler.a(packet);
//		
//	}

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
    	sendInputs(player);
    }
    
    public void sendInputs(Player player) {
    	Set<String> names = inputs.get(player.getName().toLowerCase());
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
        defaults.set("logging.console", true);
        defaults.set("logging.details", true);
        reloadConfig();
        Configuration config = getConfig();
        config.setDefaults(defaults);
        config.options().copyDefaults(true);
        saveConfig();
        toConsole = config.getBoolean("logging.console");
        details = config.getBoolean("logging.details");
        List<String> testers = config.getStringList("testers");
        for (String n : testers){
            this.testers.add(n.trim().toLowerCase());
        }
        testAll = this.testers.contains("*");
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
        final String name = player.getName().toLowerCase();
        if (!testAll && !testers.contains(name)) return false;
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
        String msg = ChatColor.YELLOW + "[TestNCP] " + ChatColor.WHITE + name + " " + ChatColor.AQUA + checkType.name() + ChatColor.WHITE + " vl " + format.format(info.getTotalVl()) + " (+" + format.format(info.getAddedVl()) + ")";
        if (details){
            final StringBuilder builder = new StringBuilder(200);
            builder.append(msg);
            if (info.needsParameters()){
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
            final String[] lagSpecs = new String[lagTicks.length];
            boolean hasLag = false;
            for (int i = 0; i < lagTicks.length; i++){
            	final long ticks = lagTicks[i];
            	final float lag = TickTask.getLag(50L * ticks);
            	if (lag > 1.0){
            		hasLag = true;
            		lagSpecs[i] = " " + i + "@" + format.format(lag);
            	}
            	else lagSpecs[i] = null;
            }
            if (hasLag){
            	builder.append("Ticks[Lag]:");
            	for (int i = 0; i < lagTicks.length; i++){
            		if (lagSpecs[i] != null) builder.append(lagSpecs[i]);
            	}
            }
            msg =  builder.toString();
        }
        if (toConsole) Bukkit.getLogger().info(ChatColor.stripColor(msg));
        final String lcName = name.toLowerCase();
        for (final Player ref : Bukkit.getOnlinePlayers()){
        	final String lcRef = ref.getName().toLowerCase();
            if (testAll || testers.contains(lcRef)){
            	Set<String> names = inputs.get(lcRef);
            	if (names == null || names.contains(lcName)) ref.sendMessage(msg);
            }
        }
    }
    
}
