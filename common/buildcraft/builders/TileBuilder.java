/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.builders;

import buildcraft.api.core.LaserKind;
import buildcraft.api.gates.IAction;
import buildcraft.api.power.IPowerReceptor;
import buildcraft.api.power.PowerHandler;
import buildcraft.api.power.PowerHandler.PowerReceiver;
import buildcraft.api.power.PowerHandler.Type;
import buildcraft.builders.blueprints.Blueprint;
import buildcraft.builders.blueprints.BlueprintBuilder;
import buildcraft.core.Box;
import buildcraft.core.EntityRobot;
import buildcraft.core.IBuilderInventory;
import buildcraft.core.IMachine;
import buildcraft.core.TileBuildCraft;
import buildcraft.core.inventory.SimpleInventory;
import buildcraft.core.network.PacketUpdate;
import buildcraft.core.network.TileNetworkData;
import buildcraft.core.proxy.CoreProxy;
import java.io.IOException;
import java.util.ListIterator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.ForgeDirection;

public class TileBuilder extends TileBuildCraft implements IBuilderInventory, IPowerReceptor, IMachine {

	private static final int SLOT_BLUEPRINT = 0;
	public @TileNetworkData
	Box box = new Box();
	private PowerHandler powerHandler;
	private EntityRobot builderRobot;
	private BlueprintBuilder blueprintBuilder;
	private ListIterator<BlueprintBuilder.SchematicBuilder> blueprintIterator;
	private boolean builderDone = true;
	private SimpleInventory inv = new SimpleInventory(28, "Builder", 64);

	public TileBuilder() {
		super();
		powerHandler = new PowerHandler(this, Type.MACHINE);
		powerHandler.configure(25, 25, 25, 100);
	}

	@Override
	public void initialize() {
		super.initialize();

		if (CoreProxy.proxy.isRenderWorld(worldObj))
			return;


	}

	@Override
	public void doWork(PowerHandler workProvider) {
		if (CoreProxy.proxy.isRenderWorld(worldObj))
			return;

//		if (builderDone)
//			return;

		initializeBuilder();
		build();
	}

	public Blueprint getBlueprint() {
		ItemStack blueprintStack = getStackInSlot(SLOT_BLUEPRINT);
		return ItemBlueprint.getBlueprint(blueprintStack);
	}

	public BlueprintBuilder getBlueprintBuilder() {
		return blueprintBuilder;
	}

	private void initializeBuilder() {
		Blueprint blueprint = getBlueprint();
		if (blueprintBuilder == null && blueprint != null) {
			box.initialize(xCoord + 1, yCoord, zCoord + 1, xCoord + 1 + blueprint.sizeX, yCoord + blueprint.sizeY, zCoord + 1 + blueprint.sizeZ);
			box.createLasers(worldObj, LaserKind.Stripes);
			blueprintBuilder = new BlueprintBuilder(blueprint, worldObj, xCoord + 1, yCoord, zCoord + 1, ForgeDirection.NORTH, null);
			blueprintIterator = blueprintBuilder.getBuilders().listIterator();
		} else if (blueprint == null) {
			box.deleteLasers();
			box.reset();
			blueprintBuilder = null;
			blueprintIterator = null;
		}
	}

	protected void build() {
		if (blueprintBuilder == null)
			return;

		if (blueprintIterator == null)
			return;

		if (!blueprintIterator.hasNext())
			return;

		float mj = 25;
		if (powerHandler.useEnergy(mj, mj, true) != mj)
			return;

		if (builderRobot == null) {
			builderRobot = new EntityRobot(worldObj, box);
			worldObj.spawnEntityInWorld(builderRobot);
		}

		if (builderRobot.readyToBuild()) {
			while (blueprintIterator.hasNext()) {
				if (builderRobot.scheduleContruction(blueprintIterator.next())) {
					powerHandler.useEnergy(0, 25, true);
					break;
				}
			}
		}
	}

	@Override
	public int getSizeInventory() {
		return inv.getSizeInventory();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		return inv.getStackInSlot(slot);
	}

	@Override
	public ItemStack decrStackSize(int slot, int amount) {
		return inv.decrStackSize(slot, amount);
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		inv.setInventorySlotContents(slot, stack);
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		return inv.getStackInSlotOnClosing(slot);
	}

	@Override
	public String getInvName() {
		return "Builder";
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if (slot == SLOT_BLUEPRINT) {
			return ItemBlueprint.getBlueprint(stack) != null;
		}
		return true;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer entityplayer) {
		return worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		inv.readFromNBT(nbt);

		if (nbt.hasKey("box")) {
			box.initialize(nbt.getCompoundTag("box"));
		}

		builderDone = nbt.getBoolean("done");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		inv.writeToNBT(nbt);

		if (box.isInitialized()) {
			NBTTagCompound boxStore = new NBTTagCompound();
			box.writeToNBT(boxStore);
			nbt.setTag("box", boxStore);
		}

		nbt.setBoolean("done", builderDone);
	}

	@Override
	public void invalidate() {
		super.invalidate();
		destroy();
	}

	@Override
	public void destroy() {
		if (box.isInitialized()) {
			box.deleteLasers();
		}

		if (builderRobot != null) {
			builderRobot.setDead();
			builderRobot = null;
		}
	}

	@Override
	public PowerReceiver getPowerReceiver(ForgeDirection side) {
		return powerHandler.getPowerReceiver();
	}

	@Override
	public void handleDescriptionPacket(PacketUpdate packet) throws IOException {
		boolean initialized = box.isInitialized();

		super.handleDescriptionPacket(packet);

		if (!initialized && box.isInitialized()) {
			box.createLasers(worldObj, LaserKind.Stripes);
		}
	}

	@Override
	public void handleUpdatePacket(PacketUpdate packet) throws IOException {
		boolean initialized = box.isInitialized();

		super.handleUpdatePacket(packet);

		if (!initialized && box.isInitialized()) {
			box.createLasers(worldObj, LaserKind.Stripes);
		}
	}

	@Override
	public void openChest() {
	}

	@Override
	public void closeChest() {
	}

	@Override
	public void updateEntity() {

		super.updateEntity();

		if (blueprintBuilder == null && box.isInitialized() && (builderRobot == null || builderRobot.done())) {

			box.deleteLasers();
			box.reset();

			if (CoreProxy.proxy.isSimulating(worldObj)) {
				sendNetworkUpdate();
			}

			return;
		}

		if (!box.isInitialized() && blueprintBuilder == null && builderRobot != null) {
			builderRobot.setDead();
			builderRobot = null;
		}
	}

	@Override
	public boolean isActive() {
		return !builderDone;
	}

	@Override
	public boolean manageFluids() {
		return false;
	}

	@Override
	public boolean manageSolids() {
		return true;
	}

	@Override
	public boolean isBuildingMaterial(int i) {
		return i != 0;
	}

	@Override
	public boolean allowAction(IAction action) {
		return false;
	}
}
