package com.programmerdan.minecraft.banstick.handler;

import com.programmerdan.minecraft.banstick.BanStick;
import com.programmerdan.minecraft.banstick.commands.BanSaveCommand;
import com.programmerdan.minecraft.banstick.commands.BanStickCommand;
import com.programmerdan.minecraft.banstick.commands.DoubleTapCommand;
import com.programmerdan.minecraft.banstick.commands.DowsingRodCommand;
import com.programmerdan.minecraft.banstick.commands.DrillDownCommand;
import com.programmerdan.minecraft.banstick.commands.ForgiveCommand;
import com.programmerdan.minecraft.banstick.commands.LoveTapCommand;
import com.programmerdan.minecraft.banstick.commands.TakeItBackCommand;

/**
 * Handles Commands for this plugin. Check plugin.yml for details!
 * 
 * @author <a href="mailto:programmerdan@gmail.com">ProgrammerDan</a>
 *
 */
public class BanStickCommandHandler {
	
	public BanStickCommandHandler(FileConfiguration config) {
		registerCommands();
	}
	
	private void registerCommands() {
		BanStick.getPlugin().getProxy().getPluginManager().registerCommand(BanStick.getPlugin(),new BanStickCommand());
		BanStick.getPlugin().getProxy().getPluginManager().registerCommand(BanStick.getPlugin(),new DoubleTapCommand());
		BanStick.getPlugin().getProxy().getPluginManager().registerCommand(BanStick.getPlugin(),new ForgiveCommand());
		BanStick.getPlugin().getProxy().getPluginManager().registerCommand(BanStick.getPlugin(),new BanSaveCommand());
		BanStick.getPlugin().getProxy().getPluginManager().registerCommand(BanStick.getPlugin(),new LoveTapCommand());
		BanStick.getPlugin().getProxy().getPluginManager().registerCommand(BanStick.getPlugin(),new TakeItBackCommand());
		BanStick.getPlugin().getProxy().getPluginManager().registerCommand(BanStick.getPlugin(),new DowsingRodCommand());
		BanStick.getPlugin().getProxy().getPluginManager().registerCommand(BanStick.getPlugin(),new DrillDownCommand());
	}

}
