package com.programmerdan.minecraft.banstick.handler;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.programmerdan.minecraft.banstick.BanStick;
import com.programmerdan.minecraft.banstick.data.BSBan;
import com.programmerdan.minecraft.banstick.data.BSIP;
import com.programmerdan.minecraft.banstick.data.BSIPData;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.data.BSShare;

import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.config.Configuration;

/**
 * Base handler for setting up event captures. Like people logging in who are about to get BanSticked.
 * 
 * @author <a href="mailto:programmerdan@gmail.com">ProgrammerDan</a>
 *
 */
public class BanStickEventHandler implements Listener {
	private float proxyThreshold = 2.0f;
	private boolean enableIPBans = true;
	private boolean enableSubnetBans = true;
	private boolean enableProxyKicks = true;
	private boolean enableProxyBans = false;
	private boolean enableNewProxyBans = false;
	private boolean enableShareBans = false;
	private String proxyBanMessage = null;
	private int shareThreshold = 0;
	private String shareBanMessage = null;

	private Logger log;
	
	public BanStickEventHandler(Configuration config) {
		// setup.
		configureEvents(config.getSection("events")); 
		registerEvents();
		this.log = BanStick.getPlugin().getLogger();
	}
	
	private void configureEvents(Configuration config) {
		this.proxyThreshold = (float) config.getDouble("proxy.threshold", proxyThreshold);
		this.enableIPBans = config.getBoolean("enable.ipBans", true);
		this.enableSubnetBans = config.getBoolean("enable.subnetBans", true);
		this.enableProxyKicks = config.getBoolean("enable.proxyKicks", true);
		this.enableProxyBans = config.getBoolean("enable.proxyBans", false);
		this.enableNewProxyBans = config.getBoolean("enable.newProxyBans", false);
		this.enableShareBans = config.getBoolean("enable.shareBans", false);
		this.proxyBanMessage = config.getString("proxy.banMessage", null);
		this.shareThreshold = config.getInt("share.threshold", shareThreshold);
		this.shareBanMessage = config.getString("share.banMessage", null);
	}
	
	private void registerEvents() {
		BanStick.getPlugin().getProxy().getPluginManager().registerListener(BanStick.getPlugin(),this);
	}
	
	@EventHandler(priority=EventPriority.LOWEST)
	public void preJoinLowest(PreLoginEvent event) {
		final InetAddress preJoinAddress = event.getAddress();
		final UUID preJoinUUID = event.getUniqueId();
		final String preJoinName = event.getName();
		// let other prejoins do their thing, we'll trigger a number of tasks now.
		
		// First, trigger a UUID based lookup. TODO: Use Async Handler
		BSPlayer player = BSPlayer.byUUID(preJoinUUID);
		if (player == null) { // create and attempt to name -- review if this is safe.
			String name = null;
			try {
				name = NameAPI.getCurrentName(preJoinUUID);
			} catch (NoClassDefFoundError ncde) { } // no namelayer
			if (name == null) {
				name = preJoinName;
			}
			log.log(Level.INFO,"New player " + preJoinUUID + ", creating record. Best guess at name: " + name);
			player = BSPlayer.create(preJoinUUID, name);
		}
		BSIP ip = BanStickDatabaseHandler.getInstance().getOrCreateIP(preJoinAddress);
		if (player != null) {
			BSBan ban = player.getBan();
			if (ban != null) {
				if (ban.getBanEndTime() != null && ban.getBanEndTime().before(new Date())) { // ban has ended.
					player.setBan(null);
				} else {
					log.info("Preventing login by " + player.getName() + " due to " + ban.toString());
					event.setCancelReason("Kick ban: " + ban.getMessage());
					event.setCancelled(true);
					return;
				}
			}
			if (player.getIPPardonTime() != null) {
				log.info("Skipping IP checks due to pardon for player " + player.getName());
				return;
			}
		}
		
		if (this.enableIPBans) {
			// Second, trigger an exact IP based lookup.
			if (ip != null) {
				List<BSBan> ipBans = BSBan.byIP(ip, false);
				for (int i = ipBans.size() - 1 ; i >= 0; i-- ) {
					//TODO: Can I have better selectivity here? What are the rules?
					BSBan pickOne = ipBans.get(i);
					if (pickOne.getBanEndTime() != null && pickOne.getBanEndTime().before(new Date())) {
						continue; // skip expired ban.
					}
					if (player != null) {
						// associate! 
						player.setBan(pickOne); // get most recent matching IP ban and use it.
					}
					log.info("Preventing login by " + player.getName() + " due to " + pickOne.toString());
					event.setCancelReason("Kick ban: " + pickOne.getMessage());
					event.setCancelled(true);
					return;
				}
			}
		}
		
		if (this.enableSubnetBans) {
			// Third, trigger a CIDR lookup. This will continue until done; it does not tie into login or async join events.
			List<BSIP> subnets = BSIP.allMatching(preJoinAddress);
			for (BSIP sip : subnets) {
				log.warning("Check for bans on IP: {0}", sip.getId());
				List<BSBan> sipBans = BSBan.byIP(sip, false);
				for (int i = sipBans.size() - 1 ; i >= 0; i-- ) {
					//TODO: Can I have better selectivity here? What are the rules?
					BSBan pickOne = sipBans.get(i);
					if (pickOne.getBanEndTime() != null && pickOne.getBanEndTime().before(new Date())) {
						continue; // skip expired ban.
					}
					if (player != null) {
						// associate! 
						player.setBan(pickOne); // get most recent matching subnet ban and use it.
					}
					log.info("Preventing login by " + player.getName() + " due to " + pickOne.toString());
					event.setCancelReason("Kick ban: " + pickOne.getMessage());
					event.setCancelled(true);
					return;
				}
			}
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void asyncPreJoinHighest(AsyncPlayerPreLoginEvent asyncEvent) {
		// TODO: idea is we'd poll futures for results here
	}
	
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void loginHighest(PlayerLoginEvent loginEvent) {
		
	}
	
	/**
	 * This handler deals with registering the player if they are new, starting their session, and triggering
	 * session sharing and vpn warning checks.
	 * 
	 * @param joinEvent
	 * 	The PlayerJoin event.
	 */
	@EventHandler(priority=EventPriority.MONITOR)
	public void joinMonitor(PlayerJoinEvent joinEvent) {
		final Player player = joinEvent.getPlayer();
		final Date playerNow = new Date();
		BanStick.getPlugin().getProxy().getScheduler().runAsync(BanStick.getPlugin(), new Runnable() {

			@Override
			public void run() {
				// Get or create player.
				if (player == null) {
					log.warning("A player check event was scheduled, but that player is already gone?");
					return;
				}
				
				BSPlayer bsPlayer = BSPlayer.byUUID(player.getUniqueId());
				
				String nowName = null;
				try {
					nowName = NameAPI.getCurrentName(player.getUniqueId());
				} catch (NoClassDefFoundError cnfe) {} // no namelayer
					
				if (nowName == null) {
					nowName = player.getDisplayName();
				}
				if (bsPlayer.getName() == null || !bsPlayer.getName().equals(nowName)) {
					bsPlayer.setName(nowName);
				}
				bsPlayer.startSession(player, playerNow);
				// The above does all the Shared Session checks, so check result here:
				if (enableShareBans && bsPlayer.getSharedPardonTime() == null ) { // no blank check
					try {
						// New approach: unpardoned shares count against your shareThreshold. If you're above shareThreshold,
						// order accounts by join date and ban all unbanned accounts using Share Ban assignments that are newest 
						// and that exceed the shareThreshold.
						
						// Don't apply share bans bidirectionally / automatically.
						List<BSShare> shares = bsPlayer.getUnpardonedShares();
						
						int cardinality = shares.size();
						if (cardinality > shareThreshold && shareThreshold > -1) { // are we multiaccount banning & are we above threshold?
							// Find the Shares above the threshold for newest accounts by create age. Ban them (might be this one).
							// For each you ban, check if online / kick.
							log.info("Player {0} has exceeding the shared account threshold. Banning the newest accounts that exceed the Threshold.", bsPlayer.getName());
							int bansIssued = 0;
							
							Set<Long> joinNoted = new HashSet<Long>();
							TreeMap<Long, BSPlayer> joinTimes = new TreeMap<Long, BSPlayer>();
							for (BSShare latest : shares) {
								BSPlayer one = latest.getFirstPlayer();
								if (!joinNoted.contains(one.getId())) {
									joinNoted.add(one.getId());
									joinTimes.put(one.getFirstAdd().getTime(), one);
								}
								BSPlayer two = latest.getSecondPlayer();
								if (!joinNoted.contains(two.getId())) {
									joinNoted.add(two.getId());
									joinTimes.put(two.getFirstAdd().getTime(), two);
								}

							}
							
							// A shareThreshold of 0 means you can't Alt; so we ban all but the oldest account.
							// Similarly a shareThreshold of 2 means you can have 2 alts; we ban all but the 3 oldest accounts.
							// TreeMaps naturally order smallest to largest, so:
							int skips = shareThreshold + 1;
							for (BSPlayer banPlayer : joinTimes.values()) {
								if (skips > 0) {
									skips --;
									continue;
								}
								if (banPlayer.getBan() != null || banPlayer.getSharedPardonTime() != null) {
									continue; // already banned, or spared from Share bans.
								}
								// Bannerino using latest unpardoned.
								BSShare useForBan = null;
								if (banPlayer.getId() == bsPlayer.getId()) { // ban person joining.
									useForBan = bsPlayer.getLatestShare();
								} else {
									List<BSShare> banShares = bsPlayer.sharesWith(banPlayer);
									if (banShares == null) continue;
									Collections.reverse(banShares); // by default list is oldest to newest 
									for (BSShare testShare : banShares) { // we want newest to oldest
										if (!testShare.isPardoned()) {
											useForBan = testShare;
											break;
										}
									}
									if (useForBan == null) {
										log.warning("Something went wrong! Claim was that the connection of {0} shared with {1} unpardoned, yet only pardoned shares found.", banPlayer.getName(), bsPlayer.getName());
										continue;
									}
								}
								BSBan doTheBan = BSBan.create(useForBan, shareBanMessage, null, false);
								banPlayer.setBan(doTheBan);
								bansIssued++;

								doKickWithCheckup(banPlayer.getUUID(), doTheBan);
							}
							BanStick.getPlugin().info("Player {0} exceeding the shared account threshold resulted in {1} bans.", bsPlayer.getName(), bansIssued);
						}
					} catch (Exception e) {
						log.warning("Failure during Share checks: " + e);
					}
				}
				
				if (bsPlayer.getBan() != null) {
					log.warning("Player " + bsPlayer.getName() + " is now banned, skipping proxy checks.");
					return;
				}
				
				// Then do VPN checks
				if (enableProxyBans || enableProxyKicks) {
					// Inject IP Hub handler.
					BanStick.getPlugin().getIPHubHandler().offer(bsPlayer.getLatestSession().getIP());
					
					try {
						if (bsPlayer.getProxyPardonTime() == null) {
							if (bsPlayer.getLatestSession().getIP() == null) {
								log.warning("Weird failure, no ip for " + bsPlayer);
								return;
							}
							if (bsPlayer.getLatestSession().getIP().getIPAddress() == null) {
								log.warning("Weird failure, no ip address for " + bsPlayer);
								return;
							}
							List<BSIPData> proxyChecks = BSIPData.allByIP(bsPlayer.getLatestSession().getIP());
							if (proxyChecks != null) {
								for (BSIPData proxyCheck : proxyChecks) {
									log.warning("Check for bans on Proxy: " + proxyCheck.getId());
									List<BSBan> proxyBans = BSBan.byProxy(proxyCheck, false);
									for (int i = proxyBans.size() - 1 ; i >= 0; i-- ) {
										BSBan pickOne = proxyBans.get(i);
										if (pickOne.getBanEndTime() != null && pickOne.getBanEndTime().before(new Date())) {
											continue; // skip expired ban.
										}
										
										if (enableProxyBans) {
											bsPlayer.setBan(pickOne); // get most recent matching proxy ban and use it.
										}
										
										if (enableProxyKicks) {
											doKickWithCheckup(player.getUniqueId(), pickOne);
										}
																		
										return;
									}
									// no ban yet; check if proxy meets /exceeds threshold for banning and new proxy bans are enabled.
									if (proxyCheck.getProxy() >= proxyThreshold && enableNewProxyBans) {
										BSBan newBan = BSBan.create(proxyCheck, proxyBanMessage, null, false);
										
										if (enableProxyBans) {
											bsPlayer.setBan(newBan);
										}
										
										if (enableProxyKicks) {
											doKickWithCheckup(player.getUniqueId(), newBan);
										}
									
										return;
									}
								}
							}
						}
					} catch (Exception e) {
						log.severe("Failed to check proxies: " + e);
					}
				}
				// etc.
			}
			
		}); 
	}
	
	public void manageDeferredProxyKick(final BSIP proxySource, final BSIPData proxyCheck) {
		log.warning("Deferred check for bans on Proxy: " + proxyCheck.getId());

		// check if proxy meets /exceeds threshold for banning and new proxy bans are enabled.
		if (proxyCheck.getProxy() >= proxyThreshold && enableNewProxyBans) {
			List<BSBan> proxyBans = BSBan.byProxy(proxyCheck, false);
			BSBan newBan = null;
			if (proxyBans != null && proxyBans.size() > 0) {
				for (BSBan checkBan : proxyBans) {
					if (checkBan.getBanEndTime() != null && checkBan.getBanEndTime().after(new Date())) {
						newBan = checkBan;
						break;
					}
				}
			}
			if (newBan == null && (proxyBans == null || proxyBans.size() == 0)) {
				newBan = BSBan.create(proxyCheck, proxyBanMessage, null, false);
			}
			
			// now look for online players that match, and kickban them.
			for (ProxiedPlayer player : BanStick.getPlugin().getProxy().getPlayers()) {
				if (player != null) {
					BSPlayer bsPlayer = BSPlayer.byUUID(player.getUniqueId());
					
					if (bsPlayer != null && bsPlayer.getProxyPardonTime() == null) {

						if (proxySource.getId() == bsPlayer.getLatestSession().getIP().getId()) { // match!
							if (enableProxyBans) {
								bsPlayer.setBan(newBan);
							}
							if (enableProxyKicks) {
								doKickWithCheckup(player.getUniqueId(), newBan);
							}
						}
					}
				}
			}
			
		
			return;
		}
		
	}

	private void doKickWithCheckup(final UUID puuid, final BSBan picked) {
		// now schedule a task to kick out the trash.
		BanStick.getPlugin().getProxy().getScheduler().runAsync(BanStick.getPlugin(), new Runnable() {

			@Override
			public void run() {
				ProxiedPlayer player = BanStick.getPlugin().getProxy().getPlayer(puuid);
				if (player != null) {
					player.disconnect(picked.getMessage());
					log.info("Removing " + player.getDisplayName() + " due to " + picked.toString());
					/*
					 * NB: Bukkit sometimes flat out ignores kicks
					new Runnable() {
						private int recheck = 0;
						@Override
						public void run() {
							// let's keep checking to make sure they are gone
							recheck ++;
							if (recheck % 10 == 9) {
								log.warning("Trying to kick " + puuid + " due to " + picked + ", on " + recheck + "th retry.");
							}
							ProxiedPlayer player = BanStick.getPlugin().getProxy().getPlayer(puuid);
							if (player != null) {
								player.disconnect(picked.getMessage());
							} else {
								this.cancel();
							}
						}
					}.runTaskTimer(BanStick.getPlugin(), 10l, 10l);
					*/
				} else {
					BanStick.getPlugin().info("On return, banning " + puuid + " due to " + picked.toString());
				}
			}
			
		});
	}
	/**
	 * Calls {@link #disconnectEvent(Player)}
	 * @param quitEvent
	 * 	The PlayerQuitEvent
	 */
	@EventHandler(priority=EventPriority.MONITOR) 
	public void quitMonitor(PlayerQuitEvent quitEvent) {
		disconnectEvent(quitEvent.getPlayer());
	}
	
	/**
	 * Calls {@link #disconnectEvent(Player)}
	 * @param kickEvent
	 * 	The PlayerKickEvent
	 */
	@EventHandler(priority=EventPriority.MONITOR)
	public void kickMonitor(PlayerKickEvent kickEvent) {
		disconnectEvent(kickEvent.getPlayer());
	}
	
	/**
	 * Ends the player's session.
	 * 
	 * @param player
	 */
	private void disconnectEvent(final UUID player) {
		BSPlayer bsPlayer = BSPlayer.byUUID(player);
		if (bsPlayer != null) {
			bsPlayer.endSession(new Date());
		}
	}
	
	/**
	 * 
	 * Attempts to ensure that regardless of shutdown order, captures session end.
	 */
	public void shutdown() {
		for (ProxiedPlayer player : BanStick.getPlugin().getProxy().getPlayers()) {
			disconnectEvent(player.getUniqueId()); // ensure some kind of session end is captured on orderly shutdown.
		}
	}
}
