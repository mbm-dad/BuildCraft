/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents
 * of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt */
package buildcraft.lib.inventory;

import com.google.common.collect.ImmutableList;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import buildcraft.api.core.IInvSlot;

public final class InventoryIterator {

    /** Deactivate constructor */
    private InventoryIterator() {}

    public static Iterable<IInvSlot> getIterable(ICapabilityProvider provider) {
        return getIterable(provider, null);
    }

    public static Iterable<IInvSlot> getIterable(ICapabilityProvider provider, EnumFacing side) {
        IItemHandler itemHandler = provider.getCapability(CapUtil.CAP_ITEMS, side);
        if (itemHandler != null) {
            return new InventoryIteratorHandler(itemHandler);
        } else if (provider instanceof IInventory) {
            return getIterable((IInventory) provider, side);
        } else {
            return ImmutableList.of();
        }
    }

    public static Iterable<IInvSlot> getIterable(IInventory inv) {
        return getIterable(inv, null);
    }

    /** Returns an Iterable object for the specified side of the inventory.
     *
     * @param inv
     * @param side
     * @return Iterable */
    public static Iterable<IInvSlot> getIterable(IInventory inv, EnumFacing side) {
        if (inv instanceof ISidedInventory) {
            return new InventoryIteratorSided((ISidedInventory) inv, side);
        }

        return new InventoryIteratorSimple(inv);
    }

}
