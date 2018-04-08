package com.programmerdan.minecraft.banstick.handler;

import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.config.Configuration;

public abstract class ImportWorker implements Runnable {

	private ScheduledTask importTask = null;
	private long delay = 100l;
	private boolean enable = false;
	
	public ImportWorker(Configuration config) {
		if (config != null && setup(config.getSection(name()))) {
			enable = internalSetup(config.getSection(name()));
		} else {
			enable = false;
		}
	}
	
	private boolean setup(Configuration config) {
		if (config == null) return false;
		delay = config.getLong("delay", delay);
		return config.getBoolean("enable", enable);
	}
	
	@Override
	public void run() {
		if (enable == true) {
			doImport();
		}
	}
	
	public abstract boolean internalSetup(Configuration config);
	public abstract void doImport();
	public abstract String name();

	public long getDelay() {
		return 0;
	}

	public void setTask(ScheduledTask task) {
		importTask = task;
	}

	public void shutdown() {
		try {
			if (enable && importTask != null) {
				importTask.cancel();
			}
		} catch (Exception e) {
			// shutdown? woops/
		}
	}

}
