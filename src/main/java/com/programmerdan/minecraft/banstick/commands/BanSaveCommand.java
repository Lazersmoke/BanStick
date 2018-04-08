package com.programmerdan.minecraft.banstick.commands;

import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.CommandSender;

import net.md_5.bungee.config.Configuration;

import com.programmerdan.minecraft.banstick.BanStick;

public class BanSaveCommand extends Command {

	public static String name = "bansave";
		
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String cmdString, String[] arguments) {
		BanStick.getPlugin().saveCache();
		
		return true;
	}

}
