package com.soulreaperaxeqol;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SoulreaperAxeQoLPlugin.class);
		RuneLite.main(args);
	}
}