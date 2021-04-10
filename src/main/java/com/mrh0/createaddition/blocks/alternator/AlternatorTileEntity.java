package com.mrh0.createaddition.blocks.alternator;

import java.util.List;

import com.mrh0.createaddition.CreateAddition;
import com.mrh0.createaddition.blocks.electric_motor.ElectricMotorBlock;
import com.mrh0.createaddition.blocks.electric_motor.ElectricMotorTileEntity;
import com.mrh0.createaddition.config.Config;
import com.mrh0.createaddition.create.KineticTileEntityFix;
import com.mrh0.createaddition.energy.InternalEnergyStorage;
import com.mrh0.createaddition.item.Multimeter;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.Lang;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public class AlternatorTileEntity extends KineticTileEntityFix {
	
	protected final InternalEnergyStorage energy;
	private LazyOptional<IEnergyStorage> lazyEnergy;
	
	private static final int 
		MAX_IN = 0,
		MAX_OUT = Config.ALTERNATOR_MAX_OUTPUT.get(),
		CAPACITY = Config.ALTERNATOR_CAPACITY.get(),
		STRESS = Config.BASELINE_STRESS.get();
	private static final double EFFICIENCY = Config.ALTERNATOR_EFFICIENCY.get();

	public AlternatorTileEntity(TileEntityType<?> typeIn) {
		super(typeIn);
		energy = new InternalEnergyStorage(CAPACITY, MAX_IN, MAX_OUT);
		lazyEnergy = LazyOptional.of(() -> energy);
		setLazyTickRate(20);
	}
	
	@Override
	public boolean addToGoggleTooltip(List<ITextComponent> tooltip, boolean isPlayerSneaking) {
		boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
		//tooltip.add(new StringTextComponent(spacing).append(new TranslationTextComponent(CreateAddition.MODID + ".tooltip.energy.stored").formatted(TextFormatting.GRAY)));
		//tooltip.add(new StringTextComponent(spacing).append(new StringTextComponent(" " + Multimeter.getString(energy) + "fe").formatted(TextFormatting.AQUA)));
		tooltip.add(new StringTextComponent(spacing).append(new TranslationTextComponent(CreateAddition.MODID + ".tooltip.energy.production").formatted(TextFormatting.GRAY)));
		tooltip.add(new StringTextComponent(spacing).append(new StringTextComponent(" " + Multimeter.format(getEnergyProductionRate((int) (isSpeedRequirementFulfilled() ? getSpeed() : 0))) + "fe/t ") // fix
				.formatted(TextFormatting.AQUA)).append(Lang.translate("gui.goggles.at_current_speed").formatted(TextFormatting.DARK_GRAY)));
		added = true;
		return added;
	}
	
	@Override
	public float calculateStressApplied() {
		float impact = STRESS/256f;
		this.lastStressApplied = impact;
		return impact;
	}
	
	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
		if(cap == CapabilityEnergy.ENERGY && (isEnergyInput(side) || isEnergyOutput(side)) && !world.isRemote)
			return lazyEnergy.cast();
		return super.getCapability(cap, side);
	}
	
	public boolean isEnergyInput(Direction side) {
		return false;
	}

	public boolean isEnergyOutput(Direction side) {
		return side != getBlockState().get(AlternatorBlock.FACING);
	}
	
	@Override
	public void fromTag(BlockState state, CompoundNBT compound, boolean clientPacket) {
		super.fromTag(state, compound, clientPacket);
		energy.read(compound);
	}
	
	@Override
	public void write(CompoundNBT compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		energy.write(compound);
	}
	
	@Override
	public void lazyTick() {
		super.lazyTick();
		//if(world.isRemote())
		//	return;
		if(Math.abs(getSpeed()) > 0 && isSpeedRequirementFulfilled())
			energy.internalProduceEnergy(getEnergyProductionRate((int)getSpeed()) * 20);
		
		/*for(Direction side : Direction.values()) {
			if(!isEnergyOutput(side))
				continue;
			energy.outputToSide(world, pos, side, MAX_OUT);
		}*/
		
		for(Direction d : Direction.values()) {
			if(!isEnergyOutput(d))
				continue;
			TileEntity te = world.getTileEntity(pos.offset(d));
			if(te == null)
				continue;
			LazyOptional<IEnergyStorage> opt = te.getCapability(CapabilityEnergy.ENERGY, d.getOpposite());
			IEnergyStorage ies = opt.orElse(null);
			if(ies == null)
				continue;
			int ext = energy.extractEnergy(ies.receiveEnergy(MAX_OUT, true), false);
			ies.receiveEnergy(ext, false);
			//int rest = ext-ies.receiveEnergy(ext, false);
			//energy.internalProduceEnergy(rest);
		}
		
		//causeBlockUpdate();
	}
	
	public static int getEnergyProductionRate(int rpm) {
		rpm = Math.abs(rpm);
		return (int)(ElectricMotorTileEntity.getEnergyConsumptionRate(rpm)  * ((double)rpm / 256d) * EFFICIENCY);//return (int)((double)Config.FE_TO_SU.get() * ((double)Math.abs(rpm)/256d) * EFFICIENCY);
	}
	
	@Override
	protected Block getStressConfigKey() {
		return AllBlocks.MECHANICAL_MIXER.get();
	}
	
	@Override
	public void remove() {
		super.remove();
		lazyEnergy.invalidate();
	}
}
