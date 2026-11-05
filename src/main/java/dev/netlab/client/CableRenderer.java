package dev.netlab.client;

import com.mojang.blaze3d.vertex.*;
import dev.netlab.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import dev.netlab.core.*;
import java.util.*;
import net.minecraft.world.level.Level;
import net.minecraft.client.gui.Font;
import com.mojang.math.Axis;

public final class CableRenderer implements BlockEntityRenderer<DeviceEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private record CachedNetwork(long tick,Network network){}
    private static final Map<Level,CachedNetwork> NETWORKS=new WeakHashMap<>();
    private static final Map<DeviceEntity,Map<Integer,Activity>> ACTIVITY=new WeakHashMap<>();
    private final net.minecraft.world.item.ItemStack supplyModel=new net.minecraft.world.item.ItemStack(NetLab.RACK_SUPPLY.get());
    private record Activity(long count,long flashed){}
    public CableRenderer(BlockEntityRendererProvider.Context c){}
    @Override public void render(DeviceEntity d,float tick,PoseStack stack,MultiBufferSource buffers,int light,int overlay){
        if(d.getLevel()==null)return;
        stack.pushPose();stack.translate(.5,0,.5);stack.mulPose(Axis.YP.rotationDegrees(-90*DeviceBlock.turns(d.getBlockState())));stack.translate(-.5,0,-.5);
        if(((DeviceBlock)d.getBlockState().getBlock()).rack){
            if(d.rackSupply){
                // ItemRenderer centres the baked model at (-.5,-.5,-.5).
                // The enclosure fits between the rack floor and the lower bay:
                // x=.0625..9375, y=.07..12, z=.06..94. Rear jacks remain at z=.96.
                stack.pushPose();stack.translate(.5,.10,.5);stack.scale(1f,.16f,1.76f);
                net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(supplyModel,net.minecraft.world.item.ItemDisplayContext.NONE,light,overlay,stack,buffers,d.getLevel(),0);
                stack.popPose();
            }
            for(int slot=0;slot<2;slot++){
                var target=slot==0?d.device:d.upperDevice;
                if(target==null||target.kind.equals("rack"))continue;
                stack.pushPose();stack.translate(.06,slot==0?.12:.62,.02);stack.scale(.88f,1,.9f);
                net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    java.util.Objects.requireNonNullElse(dev.netlab.api.RackDevices.block(target.kind),NetLab.SWITCH.get()).defaultBlockState(),stack,buffers,light,overlay);
                stack.popPose();
            }
        }
        ports(d,0,stack,buffers,light);
        if(((DeviceBlock)d.getBlockState().getBlock()).rack)ports(d,1,stack,buffers,light);
        power(d,stack,buffers,light);
        stack.popPose();
        powerCables(d,stack,buffers,light);
        d.links.forEach((p,peer)->{
            if(d.getBlockPos().asLong()>peer.pos()||d.getBlockPos().asLong()==peer.pos()&&p>peer.port())return;
            BlockPos target=BlockPos.of(peer.pos());
            if(!d.getLevel().hasChunkAt(target)||!(d.getLevel().getBlockEntity(target) instanceof DeviceEntity other))return;
            var path=CableGeometry.route(d,p,other,peer.port(),peer.anchors());var base=Vec3.atLowerCornerOf(d.getBlockPos());
            drawCable(stack,buffers,path,base,light,peer.cable(),peer.color(),peer.anchors());
            if(StudyClient.visible&&DiagnosticsOverlay.wearing()){
                double now=StudyClient.time();
                for(var flight:StudyClient.flights()){
                    var event=flight.event();boolean forward=event.from()==d.getBlockPos().asLong()&&event.fromPort()==p&&event.to()==peer.pos()&&event.toPort()==peer.port();
                    boolean reverse=event.to()==d.getBlockPos().asLong()&&event.toPort()==p&&event.from()==peer.pos()&&event.fromPort()==peer.port();
                    if(!forward&&!reverse)continue;double progress=(now-flight.start())/flight.duration();if(progress<0||progress>1)continue;
                    double t=forward?progress:1-progress;
                    Vec3 at=CableGeometry.vector(WallCablePath.sample(path,t)).subtract(base);int color=packetColor(event,false);
                    var vc=buffers.getBuffer(RenderType.entitySolid(TEXTURE));
                    // Three crossed short tubes form a visible packet marker around the wire.
                    tube(vc,stack.last(),at.add(-.045,0,0),at.add(.045,0,0),0xf000f0,color);
                    tube(vc,stack.last(),at.add(0,-.045,0),at.add(0,.045,0),0xf000f0,color);
                    tube(vc,stack.last(),at.add(0,0,-.045),at.add(0,0,.045),0xf000f0,color);
                    cargo(d,event,at,stack,buffers);
                }
            }
        });
        if(StudyClient.visible&&DiagnosticsOverlay.wearing())for(var flight:StudyClient.flights()){
            var event=flight.event();if(event.from()!=d.getBlockPos().asLong())continue;
            var source=d.portDevice(event.fromPort());int sp=event.fromPort()%128;
            boolean cellular=event.protocol().startsWith("LTE-AIR/");
            if(source==null||sp>=source.ports.size()||!cellular&&!source.ports.get(sp).medium.equals("wireless"))continue;
            var targetPos=BlockPos.of(event.to());if(!d.getLevel().hasChunkAt(targetPos)||!(d.getLevel().getBlockEntity(targetPos) instanceof DeviceEntity other))continue;
            var target=other.portDevice(event.toPort());int tp=event.toPort()%128;if(target==null||tp>=target.ports.size()||!cellular&&!target.ports.get(tp).medium.equals("wireless"))continue;
            double t=(StudyClient.time()-flight.start())/flight.duration();if(t<0||t>1)continue;
            Vec3 at=new Vec3(.5,.7,.5).lerp(Vec3.atLowerCornerOf(targetPos.subtract(d.getBlockPos())).add(.5,.7,.5),t);
            var vc=buffers.getBuffer(RenderType.entitySolid(TEXTURE));int color=packetColor(event,true);
            tube(vc,stack.last(),at.add(-.06,0,0),at.add(.06,0,0),0xf000f0,color);
            tube(vc,stack.last(),at.add(0,-.06,0),at.add(0,.06,0),0xf000f0,color);
            cargo(d,event,at,stack,buffers);
        }
    }
    private int packetColor(Study.Event e,boolean wireless){
        if(e.dropped())return 0xff3344;if("VPN".equals(e.phase())||"RADIO_ENCRYPTED".equals(e.phase()))return 0xa977ff;if("FLUID_DATA".equals(e.phase()))return 0x4baeff;if("ITEM_DATA".equals(e.phase()))return 0xffa52f;
        if(e.phase()!=null&&!e.phase().isBlank())return 0xffd77c;return e.protocol().equals("ARP")?0xffdd33:wireless?0xbb88ff:0x44ffff;
    }
    private void cargo(DeviceEntity host,Study.Event e,Vec3 at,PoseStack stack,MultiBufferSource buffers){
        boolean fluid="FLUID_DATA".equals(e.phase());if(e.dropped()||!fluid&&!"ITEM_DATA".equals(e.phase())||e.item()==null||e.item().isBlank())return;
        var id=ResourceLocation.tryParse(e.item());if(id==null)return;net.minecraft.world.item.ItemStack item;
        if(fluid){if(!net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id))return;var bucket=net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id).getBucket();item=new net.minecraft.world.item.ItemStack(bucket==net.minecraft.world.item.Items.AIR?net.minecraft.world.item.Items.BUCKET:bucket);}
        else {if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))return;item=new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));}var mc=net.minecraft.client.Minecraft.getInstance();
        stack.pushPose();stack.translate(at.x,at.y+.10,at.z);stack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());stack.scale(.18f,.18f,.18f);
        mc.getItemRenderer().renderStatic(item,net.minecraft.world.item.ItemDisplayContext.FIXED,0xf000f0,OverlayTexture.NO_OVERLAY,stack,buffers,host.getLevel(),0);stack.popPose();
    }
    private void ports(DeviceEntity entity,int bay,PoseStack stack,MultiBufferSource buffers,int light){
        Device device=bay==0?entity.device:entity.upperDevice;if(!PortLayout.supported(device))return;
        Level level=entity.getLevel();long tick=level.getGameTime();var cache=NETWORKS.get(level);
        if(cache==null||tick-cache.tick()>=5||tick<cache.tick()){cache=new CachedNetwork(tick,entity.network());NETWORKS.put(level,cache);}
        boolean rack=((DeviceBlock)entity.getBlockState().getBlock()).rack;var consumer=buffers.getBuffer(RenderType.entitySolid(TEXTURE));
        var profile=PortLayout.profile(device);
        if(profile!=null&&!profile.title().isBlank()){double tx=rack?.5:.5,ty=profile.titleY()+(rack?(bay==0?.12:.62):0),tz=rack?.02+profile.face()*.9:profile.face();frontLabel(stack,buffers,profile.title(),tx,ty,tz-.004,.0035f,0xd4e9ed);consumer=buffers.getBuffer(RenderType.entitySolid(TEXTURE));}
        var activity=ACTIVITY.computeIfAbsent(entity,e->new HashMap<>());long now=System.nanoTime();
        for(var jack:PortLayout.jacks(device,rack,bay)){
            var port=device.ports.get(jack.port()%128);boolean round=jack.medium().equals("rf")||jack.medium().startsWith("coax_")||jack.medium().startsWith("sat_rf_")||jack.medium().equals("audio");int rim=jack.console()?0x64cee9:jack.medium().equals("fiber")||jack.medium().equals("sfp")||jack.medium().equals("fronthaul")?0xffba52:round?(jack.medium().equals("audio")?0x81d49b:0xf1dc83):jack.medium().equals("serial")?0x729dc9:profile!=null?0xd8e5ec:0xa1a9b0;
            if(device.kind.equals("pc")&&jack.console()&&port.hardwareSlot.equals("usb0"))adapter(consumer,stack.last(),jack,light);
            int socketLight=profile!=null?0xf000f0:light;
            if(round){
                ring(consumer,stack.last(),jack.x(),jack.y(),jack.z(),jack.width()/2,jack.height()/2,1,0,rim,socketLight);
                ring(consumer,stack.last(),jack.x(),jack.y(),jack.z()-.001,jack.width()/2,jack.height()/2,.68,0,0x0b141b,socketLight);
                ring(consumer,stack.last(),jack.x(),jack.y(),jack.z()-.002,jack.width()/2,jack.height()/2,.18,0,0xffdf8e,socketLight);
            }else{
                face(consumer,stack.last(),jack.x(),jack.y(),jack.z(),jack.width(),jack.height(),rim,socketLight);
                face(consumer,stack.last(),jack.x(),jack.y(),jack.z()-.001,jack.width()*.76,jack.height()*.66,0x070b12,socketLight);
                if(profile!=null&&jack.medium().equals("fronthaul")){for(int side:new int[]{-1,1})face(consumer,stack.last(),jack.x()+side*jack.width()*.17,jack.y(),jack.z()-.002,jack.width()*.21,jack.height()*.34,0x89bfca,socketLight);}
                else if(profile!=null&&!jack.console()){for(int pin=0;pin<6;pin++)face(consumer,stack.last(),jack.x()+(pin-2.5)*jack.width()*.075,jack.y()+jack.height()*.14,jack.z()-.002,jack.width()*.04,jack.height()*.17,0xf0ca7d,socketLight);}
                else face(consumer,stack.last(),jack.x(),jack.y()+jack.height()*.18,jack.z()-.002,jack.width()*.50,jack.height()*.10,0xa59765,socketLight);
            }
            boolean linked=cache.network().linkUp(entity.slotId(bay),jack.port()%128);
            if(profile!=null){String caption=profile.labels().getOrDefault(jack.port()%128,device.portName(jack.port()%128));frontLabel(stack,buffers,caption,jack.x(),jack.y()+jack.height()/2+.038,jack.z()-.004,.0035f,rim);consumer=buffers.getBuffer(RenderType.entitySolid(TEXTURE));}
            else if(!jack.console()&&(device.kind.equals("switch")||device.kind.equals("router"))){frontLabel(stack,buffers,PortLayout.shortName(device.portName(jack.port()%128)),jack.x(),jack.y()+jack.height()/2+.015,jack.z()-.004,.0011f,0xd5e6ed);consumer=buffers.getBuffer(RenderType.entitySolid(TEXTURE));}
            else if(jack.console()){
                linked=false;var peer=entity.links.get(jack.port());
                if(peer!=null&&level.hasChunkAt(BlockPos.of(peer.pos()))&&level.getBlockEntity(BlockPos.of(peer.pos())) instanceof DeviceEntity other){
                    var remote=other.portDevice(peer.port());linked=remote!=null&&Hardware.powered(device)&&Hardware.powered(remote)&&ConsoleLink.compatible(device,jack.port()%128,remote,peer.port()%128);
                }
            }
            long count=port.sent+port.received;var previous=activity.get(jack.port());long flashed=previous!=null&&count>previous.count()?now:previous==null?0:previous.flashed();activity.put(jack.port(),new Activity(count,flashed));
            int led=!linked?0x313b34:now-flashed<250_000_000L?0xffc34d:0x52dc78;
            face(consumer,stack.last(),jack.x()+jack.width()*.3,jack.y()-jack.height()*.32,jack.z()-.002,jack.width()*.15,jack.height()*.12,led,linked?0xf000f0:light);
            if(jack.console()){
                var font=net.minecraft.client.Minecraft.getInstance().font;stack.pushPose();stack.translate(jack.x(),jack.y()+jack.height(),jack.z()-.004);stack.mulPose(Axis.YP.rotationDegrees(180));stack.scale(.0024f,-.0024f,.0024f);
                String label=device.kind.equals("pc")?"RS232":"CON";
                font.drawInBatch(label,-font.width(label)/2f,0,0xff64cee9,false,stack.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);stack.popPose();
                consumer=buffers.getBuffer(RenderType.entitySolid(TEXTURE));
            }
        }
    }
    private void frontLabel(PoseStack stack,MultiBufferSource buffers,String text,double x,double y,double z,float scale,int color){var font=net.minecraft.client.Minecraft.getInstance().font;stack.pushPose();stack.translate(x,y,z);stack.mulPose(Axis.YP.rotationDegrees(180));stack.scale(scale,-scale,scale);font.drawInBatch(text,-font.width(text)/2f,0,0xff000000|color,false,stack.last().pose(),buffers,Font.DisplayMode.NORMAL,0,0xf000f0);stack.popPose();}
    private void ring(VertexConsumer v,PoseStack.Pose pose,double x,double y,double z,double rx,double ry,double outer,double inner,int color,int light){var normal=new Vec3(0,0,-1);for(int i=0;i<16;i++){double a=-i*Math.PI/8,b=-(i+1)*Math.PI/8;vertex(v,pose,new Vec3(x+Math.cos(a)*rx*outer,y+Math.sin(a)*ry*outer,z),normal,light,0,0,color);vertex(v,pose,new Vec3(x+Math.cos(b)*rx*outer,y+Math.sin(b)*ry*outer,z),normal,light,1,0,color);vertex(v,pose,new Vec3(x+Math.cos(b)*rx*inner,y+Math.sin(b)*ry*inner,z),normal,light,1,1,color);vertex(v,pose,new Vec3(x+Math.cos(a)*rx*inner,y+Math.sin(a)*ry*inner,z),normal,light,0,1,color);}}
    private void adapter(VertexConsumer v,PoseStack.Pose pose,PortLayout.Jack jack,int light){
        double x=jack.x(),z=jack.z()+.002,w=.18,h=.075,depth=.11;
        face(v,pose,x,h/2,z,w,h,0x294456,light);
        Vec3[] corners={new Vec3(x-w/2,h,z),new Vec3(x-w/2,h,z+depth),new Vec3(x+w/2,h,z+depth),new Vec3(x+w/2,h,z)};
        var normal=new Vec3(0,1,0);for(int i=0;i<4;i++)vertex(v,pose,corners[i],normal,light,i==0||i==3?0:1,i<2?0:1,0x365d73);
        for(int side:new int[]{-1,1}){
            double sx=x+side*w/2;var n=new Vec3(side,0,0);Vec3[] points=side<0?new Vec3[]{new Vec3(sx,0,z),new Vec3(sx,0,z+depth),new Vec3(sx,h,z+depth),new Vec3(sx,h,z)}:new Vec3[]{new Vec3(sx,0,z),new Vec3(sx,h,z),new Vec3(sx,h,z+depth),new Vec3(sx,0,z+depth)};
            for(int i=0;i<4;i++)vertex(v,pose,points[i],n,light,i<2?0:1,i==0||i==3?1:0,0x294456);
        }
    }
    private void power(DeviceEntity host,PoseStack stack,MultiBufferSource buffers,int light){
        var v=buffers.getBuffer(RenderType.entitySolid(TEXTURE));boolean rack=((DeviceBlock)host.getBlockState().getBlock()).rack;
        if(rack&&host.rackSupply){back(v,stack.last(),.5,.095,.95,.88,.062,0x24282f,light);back(v,stack.last(),.20,.095,.964,.025,.025,host.rackPowered?0x41df65:0xd55035,0xf000f0);}
        for(var j:PowerGeometry.jacks(host)){back(v,stack.last(),j.x(),j.y(),j.z(),j.size(),j.size(),0xed9b35,light);back(v,stack.last(),j.x(),j.y(),j.z()+.001,j.size()*.64,j.size()*.64,0x101115,light);}
        for(var j:PowerGeometry.jacks(host))if(j.port()>=PowerGeometry.BOARD_IN){var target=j.port()==PowerGeometry.BOARD_IN?host.device:host.upperDevice;if(target!=null&&PortLayout.profile(target)!=null)rearLabel(stack,buffers,"FE",j.x(),j.y()+j.size()*.7,j.z()+.004,0xf000f0);}v=buffers.getBuffer(RenderType.entitySolid(TEXTURE));
        if(rack&&host.rackSupply){rearLabel(stack,buffers,"IN",.11,.15,.97,light);rearLabel(stack,buffers,"OUT",.59,.15,.97,light);v=buffers.getBuffer(RenderType.entitySolid(TEXTURE));}
        if(host.device.kind.equals("energy")){rearLabel(stack,buffers,"OUT",.5,.65,1.01,light);v=buffers.getBuffer(RenderType.entitySolid(TEXTURE));}
        for(int bay=0;bay<(rack?2:1);bay++){var d=bay==0?host.device:host.upperDevice;if(!PowerModel.board(d))continue;double x=d.kind.equals("endpoint")?.22:d.kind.equals("ap")?.2:.12,y=d.kind.equals("endpoint")?.38:d.kind.equals("ap")?.6:.205,z=d.kind.equals("endpoint")?.115:.10;if(rack){x=.06+x*.88;y+=(bay==0?.12:.62);z=.11;}
            var profile=PortLayout.profile(d);if(profile!=null){x=profile.powerX();y=profile.powerY();z=profile.face();if(rack){x=.06+x*.88;y+=(bay==0?.12:.62);z=.02+z*.9;}}
            int color=Hardware.powered(d)?0x40ec72:Hardware.ensure(d).powered?0xe14835:0xb98127;face(v,stack.last(),x,y,z-.004,.022,.022,color,0xf000f0);
            var font=net.minecraft.client.Minecraft.getInstance().font;stack.pushPose();stack.translate(x+.02,y+.006,z-.006);stack.mulPose(Axis.YP.rotationDegrees(180));stack.scale(.002f,-.002f,.002f);font.drawInBatch("PWR",-font.width("PWR"),0,0xffbcd5d1,false,stack.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);stack.popPose();v=buffers.getBuffer(RenderType.entitySolid(TEXTURE));
        }
        if(host.device.kind.equals("energy")){face(v,stack.last(),.5,.5,-.004,.18,.18,host.energy>0?0x49e577:0xe45838,0xf000f0);}
    }
    private void powerCables(DeviceEntity host,PoseStack stack,MultiBufferSource buffers,int light){for(var entry:host.powerLinks.entrySet()){
        var peer=entry.getValue();if(host.getBlockPos().asLong()>peer.pos()||host.getBlockPos().asLong()==peer.pos()&&entry.getKey()>peer.port())continue;
        var p=BlockPos.of(peer.pos());if(!host.getLevel().hasChunkAt(p)||!(host.getLevel().getBlockEntity(p) instanceof DeviceEntity other)||PowerGeometry.jack(host,entry.getKey())==null||PowerGeometry.jack(other,peer.port())==null||PowerSystem.peer(host,entry.getKey())!=other)continue;
        var path=PowerGeometry.fixedRoute(host,entry.getKey(),other,peer.port(),peer.anchors());
        drawCable(stack,buffers,path,Vec3.atLowerCornerOf(host.getBlockPos()),light,"power",0xe7a52b,peer.anchors());
    }}
    private void drawCable(PoseStack stack,MultiBufferSource buffers,List<WallCablePath.Point> path,Vec3 base,int light,String kind,int color,List<WallCablePath.Point> anchors){
        var v=buffers.getBuffer(RenderType.entitySolid(CableTextures.texture(kind)));double phase=0;
        for(int segment=1;segment<path.size();segment++){
            var a=CableGeometry.vector(path.get(segment-1)).subtract(base);var b=CableGeometry.vector(path.get(segment)).subtract(base);
            double length=a.distanceTo(b);if(length<1e-9)continue;double used=0;
            // Split at texture boundaries: no stretched textures or dependence on GL repeat state.
            while(used<length-1e-9){double u0=(phase/.25)%1;if(u0>1-1e-8)u0=0;double step=Math.min(length-used,(1-u0)*.25);
                texturedTube(v,stack.last(),a.lerp(b,used/length),a.lerp(b,(used+step)/length),light,color,CableTextures.radius(kind),(float)u0,(float)(u0+step/.25));
                phase+=step;used+=step;
            }
        }
        if(!anchors.isEmpty()){
            var clips=buffers.getBuffer(RenderType.entitySolid(TEXTURE));var delta=new Vec3(.026,.026,.026);
            for(var anchor:anchors){var at=CableGeometry.vector(anchor).subtract(base);tube(clips,stack.last(),at.subtract(delta),at.add(delta),light,0x30343a);}
        }
    }
    private void texturedTube(VertexConsumer v,PoseStack.Pose pose,Vec3 a,Vec3 b,int light,int color,double radius,float u0,float u1){
        Vec3 direction=b.subtract(a).normalize(),axis=Math.abs(direction.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0);
        var x=direction.cross(axis).normalize().scale(radius);var y=direction.cross(x).normalize().scale(radius);Vec3[] sides={x,y,x.scale(-1),y.scale(-1)};
        for(int i=0;i<4;i++){var normal=sides[i].add(sides[(i+1)%4]).normalize();float v0=i/4f,v1=(i+1)/4f;
            vertex(v,pose,a.add(sides[i]),normal,light,u0,v0,color);vertex(v,pose,b.add(sides[i]),normal,light,u1,v0,color);
            vertex(v,pose,b.add(sides[(i+1)%4]),normal,light,u1,v1,color);vertex(v,pose,a.add(sides[(i+1)%4]),normal,light,u0,v1,color);
        }
    }
    private void rearLabel(PoseStack stack,MultiBufferSource buffers,String text,double x,double y,double z,int light){var font=net.minecraft.client.Minecraft.getInstance().font;stack.pushPose();stack.translate(x,y,z);stack.scale(.0024f,-.0024f,.0024f);font.drawInBatch(text,-font.width(text)/2f,0,0xfff0bb70,false,stack.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);stack.popPose();}
    private void back(VertexConsumer v,PoseStack.Pose pose,double x,double y,double z,double w,double h,int rgb,int light){var n=new Vec3(0,0,1);vertex(v,pose,new Vec3(x-w/2,y-h/2,z),n,light,0,1,rgb);vertex(v,pose,new Vec3(x+w/2,y-h/2,z),n,light,1,1,rgb);vertex(v,pose,new Vec3(x+w/2,y+h/2,z),n,light,1,0,rgb);vertex(v,pose,new Vec3(x-w/2,y+h/2,z),n,light,0,0,rgb);}
    private void face(VertexConsumer v,PoseStack.Pose pose,double x,double y,double z,double w,double h,int rgb,int light){
        var normal=new Vec3(0,0,-1);vertex(v,pose,new Vec3(x-w/2,y-h/2,z),normal,light,0,1,rgb);vertex(v,pose,new Vec3(x-w/2,y+h/2,z),normal,light,0,0,rgb);
        vertex(v,pose,new Vec3(x+w/2,y+h/2,z),normal,light,1,0,rgb);vertex(v,pose,new Vec3(x+w/2,y-h/2,z),normal,light,1,1,rgb);
    }
    private void tube(VertexConsumer v,PoseStack.Pose pose,Vec3 a,Vec3 b,int light,int color){
        Vec3 dir=b.subtract(a).normalize(),axis=Math.abs(dir.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0);
        Vec3 x=dir.cross(axis).normalize().scale(.018),y=dir.cross(x).normalize().scale(.018);Vec3[] sides={x,y,x.scale(-1),y.scale(-1)};
        for(int i=0;i<4;i++){Vec3 n=sides[i].add(sides[(i+1)%4]).normalize();
            vertex(v,pose,a.add(sides[i]),n,light,0,0,color);vertex(v,pose,b.add(sides[i]),n,light,1,0,color);
            vertex(v,pose,b.add(sides[(i+1)%4]),n,light,1,1,color);vertex(v,pose,a.add(sides[(i+1)%4]),n,light,0,1,color);}
    }
    private void vertex(VertexConsumer v,PoseStack.Pose p,Vec3 at,Vec3 n,int light,float u,float w,int rgb){v.addVertex(p,(float)at.x,(float)at.y,(float)at.z).setColor((rgb>>16)&255,(rgb>>8)&255,rgb&255,255).setUv(u,w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,(float)n.x,(float)n.y,(float)n.z);}
    @Override public boolean shouldRenderOffScreen(DeviceEntity d){return true;}
    @Override public int getViewDistance(){return 128;}
    @Override public AABB getRenderBoundingBox(DeviceEntity d){return new AABB(d.getBlockPos()).inflate(128);}
}
