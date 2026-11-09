package dev.netlab.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StudyScreen extends Screen {
    public StudyScreen(){super(Component.literal("NetLab / Study"));}
    @Override protected void init(){
        int x=width/2-150,y=Math.max(52,height/2-64);
        double[] speeds={.1,.25,.5,1,2,4};
        for(int i=0;i<speeds.length;i++){final double speed=speeds[i];addRenderableWidget(Button.builder(Component.literal(speed+"x"),b->{StudyClient.preview(speed);net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.netlab.Packets.LearningHint(net.minecraft.core.BlockPos.ZERO,"study.speed",""));}).bounds(x+i*50,y,48,20).build());}
        addRenderableWidget(Button.builder(Component.literal(StudyClient.paused?"Продолжить":"Пауза"),b->{StudyClient.pausePlayback();rebuildWidgets();}).bounds(x,y+30,145,20).build());
        addRenderableWidget(Button.builder(Component.literal(StudyClient.visible?"Пакеты: видны":"Пакеты: скрыты"),b->{StudyClient.visible=!StudyClient.visible;b.setMessage(Component.literal(StudyClient.visible?"Пакеты: видны":"Пакеты: скрыты"));}).bounds(x+155,y+30,145,20).build());
        addRenderableWidget(Button.builder(Component.literal("Синхронно с сетью (1×)"),b->{StudyClient.synchronize();rebuildWidgets();}).bounds(x,y+60,300,20).build());
        addRenderableWidget(Button.builder(Component.literal("Очистить показ"),b->StudyClient.clear()).bounds(x,y+90,145,20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"),b->onClose()).bounds(x+155,y+90,145,20).build());
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    @Override public void render(GuiGraphics g,int x,int y,float dt){
        g.fill(0,0,width,height,0xf0102332);g.drawCenteredString(font,title,width/2,10,0xffffff);
        g.drawCenteredString(font,StudyClient.synchronizedTime?"Пакеты идут по времени серверной доставки":"Просмотр: "+StudyClient.speed+"x (не меняет доставку)",width/2,30,0x77ddff);
        super.render(g,x,y,dt);
    }
    @Override public boolean isPauseScreen(){return false;}
}
