package dev.momostudios.coldsweat.common.container;

import dev.momostudios.coldsweat.util.config.ConfigSettings;
import dev.momostudios.coldsweat.core.init.ContainerInit;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IWorldPosCallable;
import net.minecraft.util.NonNullList;
import dev.momostudios.coldsweat.core.init.BlockInit;
import dev.momostudios.coldsweat.util.math.CSMath;

public class SewingContainer extends Container
{
    private final IWorldPosCallable canInteractWithCallable;
    private final SewingInventory inventory = new SewingInventory();

    public SewingContainer(final int windowId, final PlayerInventory playerInv, IWorldPosCallable canInteractWithCallable)
    {
        super(ContainerInit.SEWING_CONTAINER_TYPE.get(), windowId);
        this.canInteractWithCallable = canInteractWithCallable;

        // Input 1
        this.addSlot(new Slot(inventory, 0, 43, 26)
        {
            @Override
            public boolean isItemValid(ItemStack stack)
            {
                return stack.getItem() instanceof ArmorItem
                    && !SewingContainer.this.isInsulatingItem(stack)
                    && !stack.getOrCreateTag().getBoolean("insulated");
            }
            @Override
            public ItemStack onTake(PlayerEntity thePlayer, ItemStack stack)
            {
                if (this.getStack().isEmpty())
                    SewingContainer.this.takeInput();
                return stack;
            }
            @Override
            public void onSlotChanged()
            {
                SewingContainer.this.testForRecipe();
            }
        });

        // Input 2
        this.addSlot(new Slot(inventory, 1, 43, 53)
        {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return isInsulatingItem(stack);
            }
            @Override
            public ItemStack onTake(PlayerEntity thePlayer, ItemStack stack)
            {
                super.onTake(thePlayer, stack);
                if (this.getStack().isEmpty())
                    SewingContainer.this.takeInput();
                return stack;
            }
            @Override
            public void onSlotChanged()
            {
                super.onSlotChanged();
                SewingContainer.this.testForRecipe();
            }
        });

        // Output
        this.addSlot(new Slot(inventory, 2, 121, 39)
        {
            @Override
            public boolean isItemValid(ItemStack stack)
            {
                return false;
            }

            @Override
            public ItemStack onTake(PlayerEntity thePlayer, ItemStack stack)
            {
                SewingContainer.this.takeOutput();
                return stack;
            }
        });

        // Main player inventory
        for (int row = 0; row < 3; row++)
        {
            for (int col = 0; col < 9; col++)
            {
                this.addSlot(new Slot(playerInv, col + (9 * row) + 9, 8 + col * 18, 166 - (4 - row) * 18 - 10));
            }
        }

        // Player Hotbar
        for (int col = 0; col < 9; col++)
        {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    public SewingContainer(final int windowId, final PlayerInventory playerInv, final PacketBuffer data)
    {
        this(windowId, playerInv, IWorldPosCallable.DUMMY);
    }

    private void takeInput()
    {
        this.getSlot(2).putStack(ItemStack.EMPTY);
    }
    private void takeOutput()
    {
        this.getSlot(0).getStack().shrink(1);
        this.getSlot(1).getStack().shrink(1);
    }
    private ItemStack testForRecipe()
    {
        ItemStack slot0Item = this.getSlot(0).getStack();
        ItemStack slot1Item = this.getSlot(1).getStack();
        ItemStack result = ItemStack.EMPTY;

        // Insulated Armor
        if (slot0Item.getItem() instanceof ArmorItem && isInsulatingItem(slot1Item)
        // Do slot types match OR is insulating item NOT armor
        &&(!(slot1Item.getItem() instanceof ArmorItem)
        || ((ArmorItem) slot0Item.getItem()).getEquipmentSlot().equals(((ArmorItem) slot1Item.getItem()).getEquipmentSlot())))
        {
            ItemStack processed = this.getSlot(0).getStack().copy();
            processed.getOrCreateTag().putBoolean("insulated", true);
            this.getSlot(2).putStack(processed);
            result = processed;
        }
        return result;
    }

    public static boolean isInsulatingItem(ItemStack item)
    {
        return ConfigSettings.INSULATING_ITEMS.get().contains(item.getItem());
    }

    public static class SewingInventory implements IInventory
    {
        private final NonNullList<ItemStack> stackList;

        public SewingInventory()
        {
            this.stackList = NonNullList.withSize(3, ItemStack.EMPTY);
        }

        @Override
        public int getSizeInventory() {
            return 3;
        }

        @Override
        public boolean isEmpty() {
            return stackList.isEmpty();
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            return index >= this.getSizeInventory() ? ItemStack.EMPTY : stackList.get(index);
        }

        @Override
        public ItemStack decrStackSize(int index, int count)
        {
            ItemStack itemstack = ItemStackHelper.getAndSplit(this.stackList, index, count);

            return itemstack;
        }

        @Override
        public ItemStack removeStackFromSlot(int index) {
            return ItemStackHelper.getAndRemove(this.stackList, index);
        }

        @Override
        public void setInventorySlotContents(int index, ItemStack stack) {
            this.stackList.set(index, stack);
        }

        @Override
        public void markDirty() {}

        @Override
        public boolean isUsableByPlayer(PlayerEntity player) {
            return true;
        }

        @Override
        public void clear() {
            this.stackList.clear();
        }
    }

    @Override
    public void onContainerClosed(PlayerEntity playerIn)
    {
        PlayerInventory playerinventory = playerIn.inventory;
        if (!playerinventory.getItemStack().isEmpty())
        {
            playerIn.dropItem(playerinventory.getItemStack(), false);
            playerinventory.setItemStack(ItemStack.EMPTY);
        }

        for (int i = 0; i < this.inventory.getSizeInventory(); i++)
        {
            ItemStack itemStack = this.inventory.getStackInSlot(i);
            if (i != 2 && !playerinventory.addItemStackToInventory(itemStack))
            {
                ItemEntity itementity = playerinventory.player.dropItem(itemStack, false);
                if (itementity != null)
                {
                    itementity.setNoPickupDelay();
                    itementity.setOwnerId(playerinventory.player.getUniqueID());
                }
            }
        }

    }

    @Override
    public boolean canInteractWith(PlayerEntity playerIn)
    {
        return isWithinUsableDistance(canInteractWithCallable, playerIn, BlockInit.SEWING_TABLE.get());
    }

    @Override
    public ItemStack transferStackInSlot(PlayerEntity playerIn, int index)
    {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot != null && slot.getHasStack())
        {
            ItemStack itemstack1 = slot.getStack();
            itemstack = itemstack1.copy();
            if (CSMath.isInRange(index, 0, 2))
            {
                if (!this.mergeItemStack(itemstack1, 3, 39, true))
                {
                    return ItemStack.EMPTY;
                }

                slot.onSlotChange(itemstack1, itemstack);
            }
            else
            {
                if (isInsulatingItem(itemstack1))
                {
                    if (!this.mergeItemStack(itemstack1, 1, 2, false))
                    {
                        slot.onSlotChange(itemstack1, itemstack);
                        return ItemStack.EMPTY;
                    }
                }
                else if (itemstack1.getItem() instanceof ArmorItem)
                {
                    if (!this.mergeItemStack(itemstack1, 0, 1, false))
                    {
                        slot.onSlotChange(itemstack1, itemstack);
                        return ItemStack.EMPTY;
                    }
                }
                else if (index == 2)
                {
                    if (!this.mergeItemStack(itemstack1, 3, 39, false))
                    {
                        slot.onSlotChange(itemstack1, itemstack);
                        return ItemStack.EMPTY;
                    }
                }
                else if (CSMath.isInRange(index, inventorySlots.size() - 9, inventorySlots.size()))
                {
                    if (!this.mergeItemStack(itemstack1, 3, 29, false))
                    {
                        slot.onSlotChange(itemstack1, itemstack);
                        return ItemStack.EMPTY;
                    }
                }
                else if (CSMath.isInRange(index, 3, inventorySlots.size() - 9))
                {
                    if (!this.mergeItemStack(itemstack1, inventorySlots.size() - 9, inventorySlots.size(), false))
                    {
                        slot.onSlotChange(itemstack1, itemstack);
                        return ItemStack.EMPTY;
                    }
                }
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty())
            {
                slot.putStack(ItemStack.EMPTY);
            }
            else
            {
                slot.onSlotChanged();
            }

            slot.onTake(playerIn, itemstack1);
        }

        return itemstack;
    }
}
