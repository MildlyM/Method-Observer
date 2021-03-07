package com.methodtournamentclient;

import net.runelite.client.config.*;

@ConfigGroup("tournament")
public interface TournamentConfig extends Config {

    @ConfigSection(
            name = "Config",
            description = "",
            position = 5
    )
    String title = "methodTournament";

    @ConfigItem(
            name = "Toggle Streaming Data",
            description = "Enables / Disables sending client inventory, and live fight information to the database",
            position = 10,
            keyName = "enable",
            section = "methodTournament"
    )
    default boolean enable() { return false; }

    @ConfigItem(
            name = "API Endpoint:",
            description = "The endpoint of the API you're using, this will be provided to you",
            position = 25,
            keyName = "endpoint",
            section = "methodTournament"
    )
    default String endpoint() { return ""; }

    @ConfigItem(
            name = "Tournament Password:",
            description = "The username of the target you want to copy",
            position = 20,
            keyName = "password",
            section = "methodTournament",
            secret = true
    )
    default String password() { return ""; }

    @ConfigItem(
            name = "Debug mode",
            description = "Enables logging, to troubleshoot any issues with the plugin",
            position = 30,
            section = "test",
            keyName = "debug"
    )
    default boolean debug() { return false; }

    @ConfigSection(
            name = "Type ::test to test connection",
            description = "",
            position = 40
    )
    String test = "test";

}