package dev.netlab.automation.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Read-only remote slot. Clicking a nonempty cell selects it for a network transfer. */
final class InventorySlotButton extends AbstractButton {
    private final Font font;
    private final ItemStack icon;
    private final int slot,count;
    private final Runnable select;
    InventorySlotButton(Font font,int x,int y,int slot,ItemStack icon,int count,String name,Runnable select){
        super(x,y,38,40,Component.literal("Слот "+slot+": "+(count>0?name+" × "+count:"пусто")));
        this.font=font;this.slot=slot;this.icon=icon;this.count=count;this.select=select;setTooltip(Tooltip.create(getMessage()));
    }
    @Override public void onPress(){if(count>0)select.run();}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
        int x=getX(),y=getY();g.fill(x,y,x+width,y+height,isHoveredOrFocused()?0xffa2cfce:0xff557b86);g.fill(x+1,y+1,x+width-1,y+height-1,0xff142732);
        g.drawString(font,"#"+slot,x+3,y+3,0xff9bc4c5,false);
        if(count>0&&!icon.isEmpty()){g.renderItem(icon,x+11,y+15);g.renderItemDecorations(font,icon,x+11,y+15,Integer.toString(count));}
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
}
