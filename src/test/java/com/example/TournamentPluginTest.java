package com.example;

import com.methodtournamentclient.TournamentPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TournamentPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TournamentPlugin.class);
		RuneLite.main(args);
	}
}