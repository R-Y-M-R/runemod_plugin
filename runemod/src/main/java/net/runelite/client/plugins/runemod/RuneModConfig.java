package net.runelite.client.plugins.runemod;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("RuneMod")
public interface RuneModConfig extends Config
{
	@ConfigItem(
			position = 1,
			keyName = "MeshID",
			name = "mesh id",
			description = ""
	)
	default int MeshID()
	{
		return 1;
	}
	@ConfigItem(
			position = 2,
			keyName = "DefID",
			name = "Definition ID",
			description = ""
	)
	default int DefID()
	{
		return 1;
	}

	@ConfigItem(
			position = 4,
			keyName = "AnimSequenceDefID",
			name = "Anim Sequence DefID",
			description = ""
	)
	default int animSequenceDefID()
	{
		return 1;
	}

	@ConfigItem(
			keyName = "spawnNPCs",
			name = "spawnNPCs",
			description = "spawnNPCs",
			position = 2
	)
	default boolean spawnNPCs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "spawnPlayers",
			name = "spawnPlayers",
			description = "spawnPlayers",
			position = 2
	)
	default boolean spawnPlayers()
	{
		return true;
	}

	@ConfigItem(
			position = 6,
			keyName = "aValue1",
			name = "aValue 1",
			description = ""
	)
	default int aValue1()
	{
		return 1;
	}

	@ConfigItem(
			position = 6,
			keyName = "VarStrID",
			name = "VarString ID",
			description = ""
	)
	default int VarStrID()
	{
		return 1;
	}

	@ConfigItem(
			position = 6,
			keyName = "VarStrValue",
			name = "VarString Value",
			description = ""
	)
	default String VarStrValue()
	{
		return "1";
	}

}