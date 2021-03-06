/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.common.base.Objects;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.api.core.IAreaProvider;
import buildcraft.api.core.IBox;

import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.PositionUtil;
import buildcraft.lib.misc.VecUtil;

/** MUTABLE integer variant of AxisAlignedBB, with a few BC-specific methods */
public class Box implements IBox {
    @SideOnly(Side.CLIENT)
    public LaserData_BC8[] laserData;
    @SideOnly(Side.CLIENT)
    public BlockPos lastMin, lastMax;

    private BlockPos min, max;

    public Box() {
        reset();
    }

    public Box(BlockPos min, BlockPos max) {
        this();
        this.min = VecUtil.min(min, max);
        this.max = VecUtil.max(min, max);
    }

    public Box(TileEntity e) {
        this(e.getPos(), e.getPos());
    }

    public void reset() {
        min = null;
        max = null;
    }

    public boolean isInitialized() {
        return min != null && max != null;
    }

    public void extendToEncompassBoth(BlockPos newMin, BlockPos newMax) {
        this.min = VecUtil.min(this.min, newMin, newMax);
        this.max = VecUtil.max(this.max, newMin, newMax);
    }

    public void setMin(BlockPos min) {
        if (min == null) return;
        this.min = min;
        this.max = VecUtil.max(min, max);
    }

    public void setMax(BlockPos max) {
        if (max == null) return;
        this.min = VecUtil.min(min, max);
        this.max = max;
    }

    public void initialize(IBox box) {
        reset();
        extendToEncompassBoth(box.min(), box.max());
    }

    public void initialize(IAreaProvider a) {
        reset();
        extendToEncompassBoth(a.min(), a.max());
    }

    public void initialize(NBTTagCompound nbt) {
        reset();
        if (nbt.hasKey("xMin")) {
            min = new BlockPos(nbt.getInteger("xMin"), nbt.getInteger("yMin"), nbt.getInteger("zMin"));
            max = new BlockPos(nbt.getInteger("xMax"), nbt.getInteger("yMax"), nbt.getInteger("zMax"));
        } else {
            min = NBTUtilBC.readBlockPos(nbt.getTag("min"));
            max = NBTUtilBC.readBlockPos(nbt.getTag("max"));
        }
        extendToEncompassBoth(min, max);
    }

    public void writeToNBT(NBTTagCompound nbt) {
        if (min != null) nbt.setTag("min", NBTUtilBC.writeBlockPos(min));
        if (max != null) nbt.setTag("max", NBTUtilBC.writeBlockPos(max));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return nbt;
    }

    public void initializeCenter(BlockPos center, int size) {
        initializeCenter(center, new BlockPos(size, size, size));
    }

    public void initializeCenter(BlockPos center, Vec3i size) {
        extendToEncompassBoth(center.subtract(size), center.add(size));
    }

    public List<BlockPos> getBlocksInArea() {
        List<BlockPos> blocks = new ArrayList<>();

        for (BlockPos pos : BlockPos.getAllInBox(min, max)) {
            blocks.add(pos);
        }

        return blocks;
    }

    public List<BlockPos> getBlocksOnEdge() {
        return PositionUtil.getAllOnEdge(min, max);
    }

    @Override
    public Box expand(int amount) {
        if (!isInitialized()) return this;
        Vec3i am = new BlockPos(amount, amount, amount);
        setMin(min().subtract(am));
        setMax(max().add(am));
        return this;
    }

    @Override
    public IBox contract(int amount) {
        return expand(-amount);
    }

    @Override
    public boolean contains(Vec3d p) {
        AxisAlignedBB bb = getBoundingBox();
        if (p.xCoord < bb.minX || p.xCoord >= bb.maxX) return false;
        if (p.yCoord < bb.minY || p.yCoord >= bb.maxY) return false;
        if (p.zCoord < bb.minZ || p.zCoord >= bb.maxZ) return false;
        return true;
    }

    public boolean contains(BlockPos i) {
        return contains(new Vec3d(i));
    }

    @Override
    public BlockPos min() {
        return min;
    }

    @Override
    public BlockPos max() {
        return max;
    }

    public BlockPos size() {
        if (!isInitialized()) return BlockPos.ORIGIN;
        return max.subtract(min).add(VecUtil.POS_ONE);
    }

    public BlockPos center() {
        return new BlockPos(centerExact());
    }

    public Vec3d centerExact() {
        return new Vec3d(size()).scale(0.5).add(new Vec3d(min()));
    }

    public Box rotateLeft() {
        Matrix4i mat = Matrix4i.makeRotLeftTranslatePositive(this);
        BlockPos newMin = mat.multiplyPosition(min);
        BlockPos newMax = mat.multiplyPosition(max);
        return new Box(newMin, newMax);
    }

    @Override
    public String toString() {
        return "Box[min = " + min + ", max = " + max + "]";
    }

    public Box extendToEncompass(IBox toBeContained) {
        if (toBeContained == null) {
            return this;
        }
        extendToEncompassBoth(toBeContained.min(), toBeContained.max());
        return this;
    }

    /** IMPORTANT: Use {@link #contains(Vec3d)}instead of the returned {@link AxisAlignedBB#isVecInside(Vec3d)} as the
     * logic is different! */
    public AxisAlignedBB getBoundingBox() {
        return new AxisAlignedBB(min, max.add(VecUtil.POS_ONE));
    }

    public Box extendToEncompass(Vec3d toBeContained) {
        setMin(VecUtil.min(min, VecUtil.convertFloor(toBeContained)));
        setMax(VecUtil.max(max, VecUtil.convertCeiling(toBeContained)));
        return this;
    }

    public Box extendToEncompass(BlockPos toBeContained) {
        setMin(VecUtil.min(min, toBeContained));
        setMax(VecUtil.max(max, toBeContained));
        return this;
    }

    @Override
    public double distanceTo(BlockPos index) {
        return Math.sqrt(distanceToSquared(index));
    }

    @Override
    public double distanceToSquared(BlockPos index) {
        return closestInsideTo(index).distanceSq(index);
    }

    public BlockPos closestInsideTo(BlockPos toTest) {
        return VecUtil.max(min, VecUtil.min(max, toTest));
    }

    @Override
    public BlockPos getRandomBlockPos(Random rand) {
        return PositionUtil.randomBlockPos(rand, min, max.add(1, 1, 1));
    }

    public boolean isOnEdge(BlockPos pos) {
        return PositionUtil.isOnEdge(min, max, pos);
    }

    public void readData(PacketBuffer stream) {
        if (stream.readBoolean()) {
            min = MessageUtil.readBlockPos(stream);
            max = MessageUtil.readBlockPos(stream);
        } else {
            min = null;
            max = null;
        }
    }

    public void writeData(PacketBuffer stream) {
        boolean isValid = isInitialized();
        stream.writeBoolean(isValid);
        if (isValid) {
            MessageUtil.writeBlockPos(stream, min);
            MessageUtil.writeBlockPos(stream, max);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) return false;
        Box box = (Box) obj;
        if (!Objects.equal(min, box.min)) return false;
        if (!Objects.equal(max, box.max)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(min, max);
    }
}
