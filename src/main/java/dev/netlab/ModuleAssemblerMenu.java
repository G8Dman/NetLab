package dev.netlab;

import dev.netlab.core.ModuleFabrication;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

public final class ModuleAssemblerMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final DataSlot selected=DataSlot.standalone();
    private boolean consuming;
    private final Container output=new SimpleContainer(1);
    private final Container inputs=new SimpleContainer(3){@Override public void setChanged(){super.setChanged();ModuleAssemblerMenu.this.slotsChanged(this);}};
    public ModuleAssemblerMenu(int id,Inventory inventory){this(id,inventory,ContainerLevelAccess.NULL);}
    public ModuleAssemblerMenu(int id,Inventory inventory,ContainerLevelAccess access){
        super(NetLab.MODULE_ASSEMBLER_MENU.get(),id);this.access=access;
        selected.set(0);addDataSlot(selected);
        for(int i=0;i<3;i++){final int input=i;addSlot(new Slot(inputs,i,196+i*46,43){
            @Override public boolean mayPlace(ItemStack stack){return ingredient(stack)==input;}
        });}
        addSlot(new Slot(output,0,242,92){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player player){return canCraft();}
            @Override public void onTake(Player player,ItemStack stack){consume();super.onTake(player,stack);}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,180+col*18,151+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,180+col*18,209));
    }
    public int selection(){return selected.get();}
    public String module(){int index=selection();return index>=0&&index<ModuleFabrication.MODULES.size()?ModuleFabrication.MODULES.get(index):null;}
    public ModuleFabrication.Cost cost(){return module()==null?null:ModuleFabrication.cost(module());}
    private static int ingredient(ItemStack stack){return stack.is(NetLab.BASIC_BOARD.get())?0:stack.is(Items.COPPER_INGOT)?1:stack.is(Items.REDSTONE)?2:-1;}
    public boolean canCraft(){var c=cost();return c!=null&&ingredient(inputs.getItem(0))==0&&ingredient(inputs.getItem(1))==1&&ingredient(inputs.getItem(2))==2&&c.available(inputs.getItem(0).getCount(),inputs.getItem(1).getCount(),inputs.getItem(2).getCount());}
    @Override public void slotsChanged(Container container){
        if(consuming)return;
        output.setItem(0,canCraft()?new ItemStack(NetLab.MODULE_ITEMS.get(module()).get()):ItemStack.EMPTY);broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player,int index){
        if(index<0||index>=ModuleFabrication.MODULES.size()||!stillValid(player))return false;
        selected.set(index);slotsChanged(inputs);return true;
    }
    private void consume(){
        if(!canCraft())return;var c=cost();consuming=true;
        inputs.removeItem(0,c.boards());inputs.removeItem(1,c.copper());inputs.removeItem(2,c.redstone());
        consuming=false;slotsChanged(inputs);
    }
    @Override public boolean stillValid(Player player){return stillValid(access,player,NetLab.MODULE_ASSEMBLER.get());}
    @Override public void removed(Player player){super.removed(player);access.execute((level,pos)->clearContainer(player,inputs));output.clearContent();}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var copy=stack.copy();
        if(index==3){
            if(!canCraft()||!moveItemStackTo(stack,4,40,true))return ItemStack.EMPTY;
            slot.onTake(player,copy);return copy;
        }
        if(index<3){if(!moveItemStackTo(stack,4,40,false))return ItemStack.EMPTY;}
        else{int input=ingredient(stack);
            if(input>=0){if(!moveItemStackTo(stack,input,input+1,false))return ItemStack.EMPTY;}
            else if(index<31){if(!moveItemStackTo(stack,31,40,false))return ItemStack.EMPTY;}
            else if(!moveItemStackTo(stack,4,31,false))return ItemStack.EMPTY;
        }
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        if(stack.getCount()==copy.getCount())return ItemStack.EMPTY;slot.onTake(player,stack);return copy;
    }
}
