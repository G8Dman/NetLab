package dev.netlab.client;

import dev.netlab.*;
import dev.netlab.core.ModuleFabrication;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

public final class ModuleAssemblerScreen extends AbstractContainerScreen<ModuleAssemblerMenu> {
    private EditBox search;
    private int scroll;
    private static final int ROWS=7;
    private final List<Integer> filtered=new ArrayList<>();
    private final List<Button> choices=new ArrayList<>();
    public ModuleAssemblerScreen(ModuleAssemblerMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=350;imageHeight=238;inventoryLabelX=180;inventoryLabelY=139;titleLabelX=10;titleLabelY=8;}
    @Override protected void init(){
        super.init();String filter=search==null?"":search.getValue();choices.clear();search=new EditBox(font,leftPos+10,topPos+25,162,18,Component.literal("Поиск модуля"));search.setMaxLength(48);search.setHint(Component.literal("Поиск модуля…"));search.setResponder(text->{scroll=0;refresh();});search.setValue(filter);addRenderableWidget(search);
        for(int row=0;row<ROWS;row++){final int index=row;choices.add(addRenderableWidget(Button.builder(Component.empty(),b->select(index)).bounds(leftPos+10,topPos+49+row*23,162,20).build()));}
        addRenderableWidget(Button.builder(Component.literal("▲"),b->{scroll=Math.max(0,scroll-ROWS);refreshButtons();}).bounds(leftPos+10,topPos+213,78,18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"),b->{scroll=Math.min(Math.max(0,filtered.size()-ROWS),scroll+ROWS);refreshButtons();}).bounds(leftPos+94,topPos+213,78,18).build());
        refresh();
    }
    private void refresh(){
        filtered.clear();String query=search.getValue().toLowerCase(Locale.ROOT);
        for(int i=0;i<ModuleFabrication.MODULES.size();i++)if(ModuleFabrication.MODULES.get(i).toLowerCase(Locale.ROOT).contains(query))filtered.add(i);
        refreshButtons();
    }
    private void refreshButtons(){
        for(int row=0;row<choices.size();row++){int at=scroll+row;var b=choices.get(row);b.visible=at<filtered.size();if(b.visible){int index=filtered.get(at);String id=ModuleFabrication.MODULES.get(index);b.setMessage(Component.literal(font.plainSubstrByWidth((index==menu.selection()?"> ":"")+id,150)));b.setTooltip(Tooltip.create(Component.literal(id)));}}
    }
    private void select(int row){
        int at=scroll+row;if(at>=filtered.size())return;int index=filtered.get(at);
        if(menu.clickMenuButton(minecraft.player,index))minecraft.gameMode.handleInventoryButtonClick(menu.containerId,index);refreshButtons();
    }
    @Override protected void containerTick(){super.containerTick();refreshButtons();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        int x=leftPos,y=topPos;g.fill(x,y,x+imageWidth,y+imageHeight,0xff182731);g.fill(x+2,y+2,x+imageWidth-2,y+imageHeight-2,0xff263b46);g.fill(x+180,y+25,x+338,y+132,0xff13212a);
        for(var slot:menu.slots){g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff7b9b9f);g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff0c171d);}
        g.drawCenteredString(font,"Платы",x+204,y+31,0xffb9e0dd);g.drawCenteredString(font,"Медь",x+250,y+31,0xffe7b57e);g.drawCenteredString(font,"Редстоун",x+296,y+31,0xffff8888);
        var cost=menu.cost();if(cost!=null){g.drawCenteredString(font,"× "+cost.boards(),x+204,y+64,0xffd2f5e9);g.drawCenteredString(font,"× "+cost.copper(),x+250,y+64,0xffd2f5e9);g.drawCenteredString(font,"× "+cost.redstone(),x+296,y+64,0xffd2f5e9);}
        g.drawCenteredString(font,"↓",x+250,y+79,0xffa7d7dc);g.drawCenteredString(font,"Забери готовый модуль",x+259,y+115,menu.canCraft()?0xff6be4a1:0xffa3b6be);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){g.drawString(font,title,titleLabelX,titleLabelY,0xffd9f4ec,false);g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xffb1c9d2,false);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(x>=leftPos+8&&x<leftPos+175&&y>topPos+45&&y<topPos+232){scroll=Math.clamp(scroll-(int)Math.signum(vertical),0,Math.max(0,filtered.size()-ROWS));refreshButtons();return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(search.isFocused()&&key!=256){if(search.keyPressed(key,scan,modifiers))return true;if(minecraft.options.keyInventory.matches(key,scan))return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
