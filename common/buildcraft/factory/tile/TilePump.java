package buildcraft.factory.tile;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.lib.fluids.SingleUseTank;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.CapUtil;
import buildcraft.lib.misc.FluidUtilBC;
import buildcraft.lib.mj.MjRedstoneBatteryReceiver;
import buildcraft.lib.net.PacketBufferBC;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TilePump extends TileMiner {
    private SingleUseTank tank = new SingleUseTank("tank", 16 * Fluid.BUCKET_VOLUME, this);
    public boolean queueBuilt = false;
    public Queue<BlockPos> queue = new PriorityQueue<>(
            Comparator.comparing(
                    blockPos -> 100_000 - (Math.pow(blockPos.getX() - pos.getX(), 2) + Math.pow(blockPos.getZ() - pos.getZ(), 2)) +
                            Math.abs(blockPos.getY() - pos.getY()) * 100_000
            )
    );
    public Map<BlockPos, List<BlockPos>> paths = new HashMap<>();

    @Override
    protected IMjReceiver createMjReceiver() {
        return new MjRedstoneBatteryReceiver(battery);
    }

    public void buildQueue() {
        queue.clear();
        paths.clear();
        List<BlockPos> nextPosesToCheck = new ArrayList<>();
        int y = pos.getY() - 1;
        List<List<BlockPos>> nextPaths = new ArrayList<>();
        while (true) {
            if (nextPosesToCheck.isEmpty()) {
                for (; y >= 0; y--) {
                    BlockPos posToCheck = new BlockPos(pos.getX(), y, pos.getZ());
                    if (BlockUtil.getFluid(world, posToCheck) != null) {
                        if (!queue.contains(posToCheck)) {
                            nextPosesToCheck.add(posToCheck);
                            nextPaths.add(new ArrayList<>(Collections.singletonList(posToCheck)));
                            break;
                        }
                    } else if(!world.isAirBlock(posToCheck)) {
                        break;
                    }
                }
                if (nextPosesToCheck.isEmpty()) {
                    break;
                }
            }
            List<BlockPos> nextPosesToCheckCopy = new ArrayList<>(nextPosesToCheck);
            nextPosesToCheck.clear();
            List<List<BlockPos>> nextPathsCopy = new ArrayList<>(nextPaths);
            nextPaths.clear();
            int i = 0;
            for (BlockPos posToCheck : nextPosesToCheckCopy) {
                List<BlockPos> path = nextPathsCopy.get(i);
                if (!queue.contains(posToCheck)) {
                    queue.add(posToCheck);
                    paths.put(posToCheck, path);
                }
                for (EnumFacing side : EnumFacing.values()) {
                    BlockPos offsetPos = posToCheck.offset(side);
                    if(Math.pow(offsetPos.getX() - pos.getX(), 2) + Math.pow(offsetPos.getZ() - pos.getZ(), 2) > Math.pow(64, 2)) {
                        continue;
                    }
                    if (BlockUtil.getFluid(world, posToCheck) != null
                            && BlockUtil.getFluid(world, offsetPos) == BlockUtil.getFluid(world, posToCheck)
                            && !queue.contains(offsetPos)
                            && !nextPosesToCheck.contains(offsetPos)
                            && !nextPosesToCheckCopy.contains(offsetPos)) {
                        nextPosesToCheck.add(offsetPos);
                        nextPaths.add(Stream.concat(path.stream(), Stream.of(offsetPos)).collect(Collectors.toList()));
                    }
                }
                i++;
            }
        }
    }

    public boolean canDrain(BlockPos blockPos) {
        Fluid fluid = BlockUtil.getFluid(world, blockPos);
        return tank.isEmpty() ? fluid != null : fluid == tank.getAcceptedFluid();
    }

    public void nextPos() {
        while (!queue.isEmpty()) {
            currentPos = queue.poll();
            if (canDrain(currentPos)) {
                return;
            }
        }
        currentPos = null;
    }

    public void updateYLevel() {
        if (currentPos != null) {
            goToYLevel(Math.min(currentPos.getY(), pos.getY()));
        } else {
            goToYLevel(pos.getY());
        }
    }

    @Override
    protected void initCurrentPos() {
        if (currentPos == null) {
            nextPos();
            updateYLevel();
        }
    }

    @Override
    public void update() {
        if (!queueBuilt && !world.isRemote) {
            buildQueue();
            queueBuilt = true;
        }

        super.update();

        FluidUtilBC.pushFluidAround(world, pos, tank);
    }

    @Override
    public void mine() {
        if (tank.isFull()) {
            return;
        }

        long target = 10000000;

        if (currentPos != null) {
            progress += battery.extractPower(0, target - progress);
            if (progress >= target) {
                FluidStack drain = BlockUtil.drainBlock(world, currentPos, false);
                if (drain != null && paths.get(currentPos).stream().allMatch(this::canDrain)) {
                    tank.fill(drain, true);
                    progress = 0;
                    int count = 0;
                    if (drain.getFluid() == FluidRegistry.WATER) {
                        for (int x = -1; x <= 1; x++) {
                            for (int z = -1; z <= 1; z++) {
                                BlockPos waterPos = currentPos.add(new BlockPos(x, 0, z));
                                if (BlockUtil.getFluid(world, waterPos) == FluidRegistry.WATER) {
                                    count++;
                                }
                            }
                        }
                    }
                    if (count < 4) {
                        world.setBlockToAir(currentPos);
                        nextPos();
                        updateYLevel();
                    }
                } else {
                    buildQueue();
                    nextPos();
                    updateYLevel();
                }
            }
        } else {
            buildQueue();
            nextPos();
            updateYLevel();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        tank.deserializeNBT(nbt.getCompoundTag("tank"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setTag("tank", tank.serializeNBT());
        return nbt;
    }

    // Networking

    @Override
    public void writePayload(int id, PacketBufferBC buffer, Side side) {
        super.writePayload(id, buffer, side);
        if (side == Side.SERVER) {
            if (id == NET_RENDER_DATA) {
                writePayload(NET_LED_STATUS, buffer, side);
            } else if (id == NET_LED_STATUS) {
                tank.writeToBuffer(buffer);
            }
        }
    }

    @Override
    public void readPayload(int id, PacketBufferBC buffer, Side side, MessageContext ctx) throws IOException {
        super.readPayload(id, buffer, side, ctx);
        if (side == Side.CLIENT) {
            if (id == NET_RENDER_DATA) {
                readPayload(NET_LED_STATUS, buffer, side, ctx);
            } else if (id == NET_LED_STATUS) {
                tank.readFromBuffer(buffer);
            }
        }
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, EnumFacing side) {
        super.getDebugInfo(left, right, side);
        left.add("fluid = " + tank.getDebugString());
        left.add("queue size = " + queue.size());
    }

    // Capabilities

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapUtil.CAP_FLUIDS) {
            return (T) tank;
        }
        return super.getCapability(capability, facing);
    }
}
