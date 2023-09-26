package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		Block.newStaticMethodThatDidNotExist();
		Block.anotherNewStaticMethodThatDidNotExist();
		Block.newStaticFieldThatDidNotExist = 0;
		Block.anotherNewStaticFieldThatDidNotExist = 1;
		Blocks.AIR.newMethodThatDidNotExist();
		Block[] x = Blocks.AIR.anotherNewMethodThatDidNotExist(Blocks.AIR);
		Blocks.AIR.newFieldThatDidNotExist = 2;
		Blocks.AIR.anotherNewFieldThatDidNotExist = 3;

		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		System.out.println("Hello Fabric world!");
	}
}
