package dev.netlab.automation.client;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Remote tank observation; no client mutation of a fluid capability. */
final class FluidTankButton extends AbstractButton {
    private final Font font;private final ItemStack icon;private final String title;private final int amount,capacity,drainable;private final Runnable select;
    FluidTankButton(Font font,int x,int y,int width,int tank,String name,String id,ItemStack icon,int amount,int capacity,int drainable,Runnable select){
        super(x,y,width,32,Component.literal("Бак "+tank+": "+name+" / "+amount+" / "+capacity+" mB"));this.font=font;this.icon=icon;this.amount=amount;this.capacity=capacity;this.drainable=drainable;this.select=select;title="#"+tank+"  "+name;
        setTooltip(Tooltip.create(Component.literal(getMessage().getString()+"\n"+id+"\nДоступно для слива: "+drainable+" mB")));
    }
    @Override public void onPress(){if(amount>0&&drainable>0)select.run();}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){int x=getX(),y=getY();g.fill(x,y,x+width,y+height,isHoveredOrFocused()?0xffa2cfce:0xff557b86);g.fill(x+1,y+1,x+width-1,y+height-1,0xff142732);if(!icon.isEmpty())g.renderItem(icon,x+5,y+6);g.drawString(font,font.plainSubstrByWidth(title,width-34),x+28,y+4,0xffddf1ed,false);g.drawString(font,amount+" / "+capacity+" mB",x+28,y+15,0xffa2cfce,false);g.fill(x+3,y+27,x+width-3,y+30,0xff314b58);int fill=capacity<=0?0:(int)Math.clamp((long)amount*(width-6)/capacity,0,width-6);g.fill(x+3,y+27,x+3+fill,y+30,0xff4aa9dd);}
    @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
}
