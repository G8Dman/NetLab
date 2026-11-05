package dev.netlab.automation.client;

import com.google.gson.*;
import dev.netlab.automation.ManagementPackets;
import dev.netlab.automation.core.AutomationProgram;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Desktop client contains no authority or local inventory mutations. */
public final class ManagementScreen extends Screen {
    public final BlockPos pc;
    private final Screen parent;
    private JsonObject state=new JsonObject();
    private final Map<String,String> values=new HashMap<>();
    private final Map<String,EditBox> fields=new LinkedHashMap<>();
    private final List<String[]> labels=new ArrayList<>();
    private final Map<String,String> endpointNames=new HashMap<>();
    private JsonArray recentServers=new JsonArray();
    private boolean historyRequested,hideEmptySlots,fluidMode,tankInspector;
    private record IconAt(ItemStack stack,int x,int y){}
    private final List<IconAt> choiceIcons=new ArrayList<>();
    private int pickerX,pickerY,pickerWidth,pickerTab,ruleIndex=-1,patternIndex=-1;
    private ManagementDropdown dropdown;
    private String pendingAction="",reopenDropdown="";
    private boolean openItemsAfterInspect;
    private String token="",status="Введи IP локального сервера и его пароль";
    private int tab,rowPage,receiverPage;private boolean gridPage,inspector,busy;
    private int left,w;
    public ManagementScreen(BlockPos pc,Screen parent){super(Component.literal("NetLab / Управление автоматикой"));this.pc=pc;this.parent=parent;}
    private JsonArray array(String name){return state.has(name)&&state.get(name).isJsonArray()?state.getAsJsonArray(name):new JsonArray();}
    private void remember(){fields.forEach((k,v)->values.put(k,v.getValue()));}
    private void set(String key,String text){values.put(key,text);if(fields.containsKey(key))fields.get(key).setValue(text);}
    private String value(String key,String fallback){return values.getOrDefault(key,fallback);}
    private EditBox field(String key,String label,int x,int y,int width,String fallback){
        var box=new EditBox(font,x,y,width,18,Component.literal(label));box.setMaxLength(key.equals("ticket")?36:64);box.setValue(value(key,fallback));fields.put(key,box);labels.add(new String[]{label,Integer.toString(x),Integer.toString(y-10)});addRenderableWidget(box);return box;
    }
    private Button button(String text,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->{remember();action.run();}).bounds(x,y,width,19).build());}
    private void send(String action,JsonObject q){if(busy){status="Дождись завершения текущего сетевого запроса";return;}busy=true;pendingAction=action;status="Запрос отправлен по сети…";PacketDistributor.sendToServer(new ManagementPackets.Request(pc,token,action,q.toString()));}
    private JsonObject form(){remember();var q=new JsonObject();for(var e:values.entrySet())q.addProperty(e.getKey(),e.getValue());return q;}
    public void update(String json){var reply=JsonParser.parseString(json).getAsJsonObject();if(reply.has("history"))recentServers=reply.getAsJsonArray("history");if(reply.has("historyOnly"))return;busy=false;if(reply.has("status"))status=reply.get("status").getAsString();if(reply.has("logout")&&reply.get("logout").getAsBoolean()){token="";state=new JsonObject();endpointNames.clear();}if(reply.has("token")){token=reply.get("token").getAsString();state=reply;for(var entry:array("endpoints")){var node=entry.getAsJsonObject();endpointNames.put(node.get("ip").getAsString(),node.get("name").getAsString());}}
        boolean items=openItemsAfterInspect&&pendingAction.equals("inspect")&&reply.has("token")&&matchingSnapshot();String reopen=reopenDropdown;
        if(pendingAction.equals("ruleDelete")||pendingAction.equals("ruleAdd")||pendingAction.equals("refresh"))ruleIndex=-1;if(pendingAction.equals("patternDelete")||pendingAction.equals("patternAdd")||pendingAction.equals("refresh"))patternIndex=-1;
        openItemsAfterInspect=false;reopenDropdown="";pendingAction="";rebuild();
        if(!token.isEmpty()){if(items&&(tab==1||tab==2))openItemDropdown();else if(reopen.equals("jobs")&&tab==4)openJobs();else if(!reopen.isEmpty()&&!reopen.equals("jobs")&&pickerTab==tab)openNodeDropdown(reopen,pickerX,pickerY,pickerWidth);}
    }
    private void rebuild(){remember();dropdown=null;clearWidgets();init();}
    private String first(){return array("endpoints").isEmpty()?"":array("endpoints").get(0).getAsJsonObject().get("ip").getAsString();}
    private int itemLimit(){return state.has("hardware")?state.getAsJsonObject("hardware").get("itemLimit").getAsInt():64;}
    @Override protected void init(){
        dropdown=null;fields.clear();labels.clear();choiceIcons.clear();w=Math.min(690,width-20);left=(width-w)/2;
        if(token.isEmpty()){
            if(!historyRequested){historyRequested=true;PacketDistributor.sendToServer(new ManagementPackets.Request(pc,"","history","{}"));}
            int x=width/2-130,y=Math.max(58,height/2-48);
            var server=field("server","IP сервера (та же LAN/VLAN)",x,y,226,"");button("▼",x+232,y,28,()->openServerHistory(x,y+20));var password=field("password","Пароль сервера",x,y+38,260,"");
            server.setResponder(text->{values.put("server",text);values.remove("password");password.setValue("");});
            password.setFormatter((text,cursor)->net.minecraft.util.FormattedCharSequence.forward("*".repeat(text.length()),Style.EMPTY));
            button("Войти",x,y+65,125,()->{var q=form();send("login",q);values.remove("password");password.setValue("");});button("Назад",x+135,y+65,125,this::onClose);return;
        }
        String[] titles={"Интерфейсы","Отправить","Логика","Рецепты","Задания"};int tw=w/5;
        button("Сервер",left+w-96,6,96,()->send("hardware",new JsonObject()));
        for(int i=0;i<5;i++){int t=i;button((tab==i?"> ":"")+titles[i],left+i*tw,30,tw-2,()->{tab=t;rowPage=0;rebuild();});}
        button("Обновить",left,height-24,90,()->send(tab==0&&inspector?"inspect":"refresh",form()));button("Выход",left+w-80,height-24,80,this::onClose);
        if(tab==2||tab==3)button(state.has("running")&&state.get("running").getAsBoolean()?"Остановить программу":"Запустить программу",left+95,height-24,Math.min(190,w-180),()->send("running",new JsonObject()));
        int half=(w-12)/2;
        if(tab==0){
            if(inspector){inventoryGrid(half);return;}
            var endpoints=array("endpoints");int rows=Math.max(1,(height-145)/23);
            for(int i=0;i<rows;i++){int at=rowPage*rows+i;if(at>=endpoints.size())break;var node=endpoints.get(at).getAsJsonObject();String ip=node.get("ip").getAsString();String text=ip+"  "+node.get("name").getAsString()+"  E:"+node.get("extract").getAsBoolean()+" I:"+node.get("insert").getAsBoolean()+" C:"+node.get("control").getAsBoolean();
                button(font.plainSubstrByWidth(text,w-8),left,58+i*23,w,()->{if(busy)return;set("source",ip);state.remove("snapshot");rowPage=0;inspector=true;send("inspect",form());rebuild();});}
            button("<",left, height-79,36,()->{rowPage=Math.max(0,rowPage-1);rebuild();});button(">",left+40,height-79,36,()->{rowPage=Math.min(Math.max(0,(endpoints.size()-1)/rows),rowPage+1);rebuild();});
            button(state.has("running")&&state.get("running").getAsBoolean()?"Правила: работают":"Правила: остановлены",left+90,height-79,Math.min(240,w-90),()->send("running",new JsonObject()));
            button("LAN <",left+95,height-24,65,()->networkPage(-1));button("LAN >",left+165,height-24,65,()->networkPage(1));
        }else if(tab==1){
            transferForm(half);
        }else if(tab==2){
            nodeField("source","Источник — имя / IP",left,65,half,first());nodeField("destination","Получатель / IP через шлюз",left+half+12,65,half,"");
            itemField("ruleItem","Предмет",left,98,w-88,"minecraft:coal");field("count","До "+itemLimit(),left+w-78,98,78,"16");
            field("sourceSlot","Слот источника (-1 = любой)",left,131,half,"-1");field("destinationSlot","Слот назначения (-1 = любой)",left+half+12,131,half,"-1");
            if(AutomationProgram.ANY_ITEM.equals(value("ruleItem",""))){labels.add(new String[]{"Условие",Integer.toString(left),"155"});var drain=button("Отправлять всё из источника",left,165,half,()->{});drain.active=false;drain.setTooltip(Tooltip.create(Component.literal("Порог запаса получателя не используется; партия задаёт размер одной отправки")));}
            else field("minimum","Если количество меньше",left,165,half,"64");
            button("Добавить правило",left+half+12,165,half,()->{var q=form();q.addProperty("item",value("ruleItem","minecraft:coal"));send("ruleAdd",q);});
            button(ruleIndex>=0?"Правило "+(ruleIndex+1)+" ▼":"Выбрать правило ▼",left,198,half,this::openRules);button("Удалить выбранное",left+half+12,198,half,()->{var q=new JsonObject();q.addProperty("index",ruleIndex);send("ruleDelete",q);}).active=ruleIndex>=0&&ruleIndex<array("rules").size();
        }else if(tab==3){
            if(!gridPage){
                field("name","Имя шаблона",left,65,half,"recipe-1");nodeField("crafter","Интерфейс верстака",left+half+12,65,half,"");
                nodeField("source","Склад ингредиентов",left,98,half,first());nodeField("destination","Куда вернуть результат",left+half+12,98,half,first());
                itemField("item","Результат",left,131,w-88,"minecraft:torch");field("minimum","Запас",left+w-78,131,78,"64");
                button("Настроить сетку 3×3",left,158,half,()->{gridPage=true;rebuild();});button("Сохранить шаблон",left+half+12,158,half,this::pattern);
                button(patternIndex>=0?"Шаблон: "+value("name","")+" ▼":"Выбрать шаблон ▼",left,190,half,this::openPatterns);button("Удалить выбранный",left+half+12,190,half,()->{var q=new JsonObject();q.addProperty("index",patternIndex);send("patternDelete",q);}).active=patternIndex>=0&&patternIndex<array("patterns").size();
            }else{int cell=(w-16)/3;for(int row=0;row<3;row++)for(int col=0;col<3;col++){int i=row*3+col;itemField("grid"+i,"Слот "+i,left+col*(cell+8),68+row*37,cell,"-");}button("Назад к шаблону",left,180,w,()->{gridPage=false;rebuild();});}
        }else{
            field("ticket","Билет / вручную для перехваченного",left,65,w-34,"");button("▼",left+w-28,65,28,this::openJobs);nodeField("destination","Получатель / IP через шлюз",left,98,w,first());button("Погасить билет",left,123,half,()->send("claim",form()));button("Вернуть исходному владельцу",left+half+12,123,half,()->send("return",form()));
            button("Выбрать задание из журнала ▼",left,156,w,this::openJobs);
            button("Журнал <",left+95,height-24,80,()->journalPage(-1));button("Журнал >",left+180,height-24,80,()->journalPage(1));
        }
    }
    private String nodeLabel(String ip){return ip.isBlank()?"Выбери интерфейс…":endpointNames.getOrDefault(ip,"Интерфейс")+" | "+ip;}
    private void openServerHistory(int x,int y){
        var options=new ArrayList<ManagementDropdown.Entry>();
        for(var entry:recentServers){String ip=entry.getAsString();options.add(new ManagementDropdown.Entry(ip,"Успешный вход • пароль введи вручную",ItemStack.EMPTY,()->{set("server",ip);set("password","");setFocused(fields.get("password"));}));}
        dropdown=new ManagementDropdown(font,x,y,260,height,"Ранее подключённые серверы",options,"",null,null,()->dropdown=null);
    }
    private void inventoryGrid(int half){
        button("< Список устройств",left,57,half,()->{inspector=false;rowPage=0;rebuild();});
        addRenderableWidget(Checkbox.builder(Component.literal(tankInspector?"Убрать пустые баки":"Убрать пустые слоты"),font).pos(left+half+12,57).maxWidth(half).selected(hideEmptySlots).onValueChange((box,selected)->{hideEmptySlots=selected;rowPage=0;rebuild();}).build());
        labels.add(new String[]{font.plainSubstrByWidth(nodeLabel(value("source","")),w-130),Integer.toString(left),"83"});
        button(tankInspector?"Баки ▼":"Предметы ▼",left+w-120,80,120,()->{tankInspector=!tankInspector;rowPage=0;rebuild();});
        if(tankInspector){tankGrid();return;}
        var cells=new ArrayList<JsonObject>();for(var entry:inventory()){var cell=entry.getAsJsonObject();if(!hideEmptySlots||cell.get("count").getAsInt()>0)cells.add(cell);}
        int cols=Math.max(1,Math.min(12,w/40)),rows=Math.max(1,(height-180)/42),perPage=cols*rows,pages=Math.max(1,(cells.size()+perPage-1)/perPage);rowPage=Math.clamp(rowPage,0,pages-1);
        int start=left+(w-cols*40)/2;
        for(int i=0;i<perPage;i++){int at=rowPage*perPage+i;if(at>=cells.size())break;var cell=cells.get(at);int count=cell.get("count").getAsInt();var stack=icon(cell.get("item").getAsString());
            addRenderableWidget(new InventorySlotButton(font,start+i%cols*40,105+i/cols*42,cell.get("slot").getAsInt(),stack,count,count>0?itemName(cell):"пусто",()->{fluidMode=false;set("item",cell.get("item").getAsString());set("sourceSlot",cell.get("slot").getAsString());set("count",Integer.toString(Math.min(16,count)));tab=1;rebuild();}));
        }
        if(cells.isEmpty())labels.add(new String[]{matchingSnapshot()?"Нет слотов для показа":"Ожидание содержимого по сети…",Integer.toString(left),"111"});
        button("< Слоты",left,height-78,90,()->{rowPage=Math.max(0,rowPage-1);rebuild();}).active=rowPage>0;
        button("Слоты >",left+95,height-78,90,()->{rowPage=Math.min(pages-1,rowPage+1);rebuild();}).active=rowPage+1<pages;
        labels.add(new String[]{font.plainSubstrByWidth("Страница "+(rowPage+1)+" / "+pages+" • слотов: "+cells.size(),Math.max(1,w-200)),Integer.toString(left+195),Integer.toString(height-72)});
        var snapshot=matchingSnapshot()?state.getAsJsonObject("snapshot"):new JsonObject();
        if(snapshot.has("block")&&status.equals("Состояние получено по сети"))status=snapshot.get("block").getAsString()+" / "+snapshot.get("target")+" / "+(snapshot.has("workState")?snapshot.get("workState").getAsString():"")+(snapshot.has("energy")?" / FE "+snapshot.get("energy"):"");
    }
    private void nodeField(String key,String label,int x,int y,int width,String fallback){
        var box=field(key,label,x,y,width-34,fallback);box.setTooltip(Tooltip.create(Component.literal(nodeLabel(box.getValue()))));button("▼",x+width-28,y,28,()->openNodeDropdown(key,x,y+20,width));
    }
    private void itemField(String key,String label,int x,int y,int width,String fallback){
        values.putIfAbsent(key,fallback);String id=value(key,fallback);boolean any=key.equals("ruleItem")&&AutomationProgram.ANY_ITEM.equals(id);var stack=any?new ItemStack(net.minecraft.world.item.Items.HOPPER):icon(id);String name=any?"Любой предмет":stack.isEmpty()?"Пусто":stack.getHoverName().getString();labels.add(new String[]{label,Integer.toString(x),Integer.toString(y-10)});
        var b=button(font.plainSubstrByWidth(name,width-48)+" ▼",x,y,width,()->openRegistryItems(key,x,y+20,width));b.setTooltip(Tooltip.create(Component.literal(name+" / "+id)));if(!stack.isEmpty())choiceIcons.add(new IconAt(stack,x+4,y+1));
    }
    private void openRegistryItems(String key,int x,int y,int width){
        var options=new ArrayList<ManagementDropdown.Entry>();
        if(key.equals("ruleItem"))options.add(new ManagementDropdown.Entry("Любой предмет","Отправлять содержимое выбранного слота без порога запаса",new ItemStack(net.minecraft.world.item.Items.HOPPER),()->{set(key,AutomationProgram.ANY_ITEM);rebuild();}));
        if(key.startsWith("grid"))options.add(new ManagementDropdown.Entry("Пустая ячейка","Не класть предмет",ItemStack.EMPTY,()->{set(key,"-");rebuild();}));
        var items=new ArrayList<ManagementDropdown.Entry>();
        for(var item:BuiltInRegistries.ITEM){var stack=new ItemStack(item);if(stack.isEmpty())continue;String id=BuiltInRegistries.ITEM.getKey(item).toString();items.add(new ManagementDropdown.Entry(stack.getHoverName().getString(),id,stack,()->{set(key,id);rebuild();}));}
        items.sort(Comparator.comparing(e->e.title().toLowerCase(Locale.ROOT)));options.addAll(items);
        int popupWidth=Math.min(w,Math.max(200,width));dropdown=new ManagementDropdown(font,Math.clamp(x,left,left+w-popupWidth),y,popupWidth,height,"Выбор предмета",options,"",null,null,()->dropdown=null);
    }
    private void openRules(){
        var options=new ArrayList<ManagementDropdown.Entry>();var rules=array("rules");
        for(int i=0;i<rules.size();i++){int selected=i;var rule=rules.get(i).getAsJsonObject();boolean any=AutomationProgram.ANY_ITEM.equals(rule.get("item").getAsString());var stack=any?new ItemStack(net.minecraft.world.item.Items.HOPPER):icon(rule.get("item").getAsString());
            options.add(new ManagementDropdown.Entry("#"+(i+1)+" • "+(any?"Любой предмет":stack.getHoverName().getString()),rule.get("source").getAsString()+" → "+rule.get("destination").getAsString()+(any?" • отправлять всё": " • меньше "+rule.get("minimum").getAsString())+", партия "+rule.get("batch").getAsString(),stack,()->{ruleIndex=selected;for(String key:List.of("source","destination","sourceSlot","destinationSlot","minimum"))set(key,rule.get(key).getAsString());set("ruleItem",rule.get("item").getAsString());set("count",rule.get("batch").getAsString());status="Выбрано правило "+(selected+1)+". «Добавить правило» создаёт новое.";rebuild();}));
        }
        dropdown=new ManagementDropdown(font,left,218,w,height,"Сохранённые правила",options,"",null,null,()->dropdown=null);
    }
    private void openPatterns(){
        var options=new ArrayList<ManagementDropdown.Entry>();var patterns=array("patterns");
        for(int i=0;i<patterns.size();i++){int selected=i;var p=patterns.get(i).getAsJsonObject();var stack=icon(p.get("output").getAsString());
            options.add(new ManagementDropdown.Entry(p.get("name").getAsString()+" • "+stack.getHoverName().getString(),p.get("source").getAsString()+" → "+p.get("crafter").getAsString()+" → "+p.get("destination").getAsString(),stack,()->{patternIndex=selected;for(String key:List.of("name","source","crafter","destination","minimum"))set(key,p.get(key).getAsString());set("item",p.get("output").getAsString());var grid=p.getAsJsonArray("grid");for(int cell=0;cell<9;cell++)set("grid"+cell,grid.get(cell).getAsString());status="Выбран шаблон. Сохранение с тем же именем обновит его.";rebuild();}));
        }
        dropdown=new ManagementDropdown(font,left,210,w,height,"Сохранённые рецепты",options,"",null,null,()->dropdown=null);
    }
    private void openJobs(){
        var options=new ArrayList<ManagementDropdown.Entry>();
        for(var entry:array("jobs")){var job=entry.getAsJsonObject();String ticket=job.get("ticket").getAsString();options.add(new ManagementDropdown.Entry(ticket.substring(0,8)+" • "+job.get("state").getAsString()+" × "+job.get("count").getAsString()+(job.has("unit")?job.get("unit").getAsString():""),job.get("note").getAsString(),job.has("fluid")?fluidIcon(job.get("fluid").getAsString()):job.has("item")?icon(job.get("item").getAsString()):ItemStack.EMPTY,()->{set("ticket",ticket);status=job.get("note").getAsString();rebuild();}));}
        int page=state.has("jobPage")?state.get("jobPage").getAsInt():0,pages=state.has("jobPages")?state.get("jobPages").getAsInt():1;
        dropdown=new ManagementDropdown(font,left,85,w,height,"Задания",options,(page+1)+" / "+pages,page>0?()->{reopenDropdown="jobs";journalPage(-1);}:null,page+1<pages?()->{reopenDropdown="jobs";journalPage(1);}:null,()->dropdown=null);
    }
    private boolean matchingSnapshot(){return state.has("snapshot")&&state.getAsJsonObject("snapshot").has("ip")&&state.getAsJsonObject("snapshot").get("ip").getAsString().equals(value("source",""));}
    private JsonArray inventory(){return matchingSnapshot()&&state.getAsJsonObject("snapshot").has("inventory")?state.getAsJsonObject("snapshot").getAsJsonArray("inventory"):new JsonArray();}
    private JsonArray tanks(){return matchingSnapshot()&&state.getAsJsonObject("snapshot").has("tanks")?state.getAsJsonObject("snapshot").getAsJsonArray("tanks"):new JsonArray();}
    private ItemStack fluidIcon(String id){var key=ResourceLocation.tryParse(id);if(key==null||!BuiltInRegistries.FLUID.containsKey(key))return new ItemStack(net.minecraft.world.item.Items.BUCKET);var bucket=BuiltInRegistries.FLUID.get(key).getBucket();return new ItemStack(bucket==net.minecraft.world.item.Items.AIR?net.minecraft.world.item.Items.BUCKET:bucket);}
    private JsonObject selectedFluid(){for(var row:tanks()){var tank=row.getAsJsonObject();if(tank.get("amount").getAsInt()>0&&tank.get("fluid").getAsString().equals(value("fluid",""))&&tank.get("componentsHash").getAsString().equals(value("fluidHash",""))&&tank.get("drainable").getAsInt()>0)return tank;}return null;}
    private void selectFluid(JsonObject tank){set("fluid",tank.get("fluid").getAsString());set("fluidHash",tank.get("componentsHash").getAsString());int amount;try{amount=Integer.parseInt(value("amount","1000"));}catch(NumberFormatException e){amount=1000;}set("amount",Integer.toString(Math.clamp(amount,1,Math.max(1,Math.min(16000,tank.get("drainable").getAsInt())))));fluidMode=true;tab=1;rebuild();}
    private void tankGrid(){
        var entries=new ArrayList<JsonObject>();for(var row:tanks()){var tank=row.getAsJsonObject();if(!hideEmptySlots||tank.get("amount").getAsInt()>0)entries.add(tank);}int rows=Math.max(1,(height-185)/35),pages=Math.max(1,(entries.size()+rows-1)/rows);rowPage=Math.clamp(rowPage,0,pages-1);
        for(int i=0;i<rows&&rowPage*rows+i<entries.size();i++){var tank=entries.get(rowPage*rows+i);addRenderableWidget(new FluidTankButton(font,left,105+i*35,w,tank.get("tank").getAsInt(),tank.get("name").getAsString(),tank.get("fluid").getAsString(),fluidIcon(tank.get("fluid").getAsString()),tank.get("amount").getAsInt(),tank.get("capacity").getAsInt(),tank.get("drainable").getAsInt(),()->selectFluid(tank)));}
        if(entries.isEmpty())labels.add(new String[]{"Нет доступных баков FluidHandler на этой стороне",Integer.toString(left),"111"});
        button("< Баки",left,height-78,90,()->{rowPage=Math.max(0,rowPage-1);rebuild();}).active=rowPage>0;button("Баки >",left+95,height-78,90,()->{rowPage=Math.min(pages-1,rowPage+1);rebuild();}).active=rowPage+1<pages;
        labels.add(new String[]{"Баки: "+entries.size()+" • "+(rowPage+1)+" / "+pages,Integer.toString(left+195),Integer.toString(height-72)});
    }
    private ItemStack icon(String id){var key=ResourceLocation.tryParse(id);return key!=null&&BuiltInRegistries.ITEM.containsKey(key)?new ItemStack(BuiltInRegistries.ITEM.get(key)):ItemStack.EMPTY;}
    private String itemName(JsonObject cell){return cell.has("customName")?cell.get("customName").getAsString():icon(cell.get("item").getAsString()).getHoverName().getString();}
    private JsonObject selectedItem(){
        for(var entry:inventory()){var cell=entry.getAsJsonObject();if(cell.get("count").getAsInt()>0&&cell.get("item").getAsString().equals(value("item",""))&&(value("sourceSlot","-1").equals("-1")||cell.get("slot").getAsString().equals(value("sourceSlot","-1"))))return cell;}
        return null;
    }
    private void transferForm(int half){
        labels.add(new String[]{"Источник",Integer.toString(left),"55"});
        var source=button(font.plainSubstrByWidth(nodeLabel(value("source","")),half-22)+" ▼",left,65,half,()->openNodeDropdown("source"));source.setTooltip(Tooltip.create(Component.literal(nodeLabel(value("source","")))));
        field("destination","Получатель IP / вручную через шлюз",left+half+12,65,half-34,"");button("▼",left+w-30,65,30,()->openNodeDropdown("destination"));
        labels.add(new String[]{fluidMode?"Жидкость из выбранного интерфейса":"Предмет из выбранного интерфейса",Integer.toString(left),"88"});
        var selected=fluidMode?selectedFluid():selectedItem();String title=selected==null?(fluidMode?"Выбери жидкость…":"Выбери предмет…"):fluidMode?selected.get("name").getAsString()+" / "+selected.get("amount").getAsString()+" mB":itemName(selected)+" × "+selected.get("count").getAsString();
        var items=button(font.plainSubstrByWidth(title,w-145)+" ▼",left,98,w-88,this::openItemDropdown);
        if(selected!=null)items.setTooltip(Tooltip.create(Component.literal(fluidMode?title+" / "+selected.get("fluid").getAsString():itemName(selected)+" / "+selected.get("item").getAsString()+" / слот "+selected.get("slot").getAsString())));
        field(fluidMode?"amount":"count",fluidMode?"Объём mB":"До "+itemLimit(),left+w-78,98,78,fluidMode?"1000":"16");
        if(fluidMode)button("1 ведро = 1000 mB • бак выбирает сам подключённый блок",left,131,w,()->{}).active=false;
        else{field("sourceSlot","Слот источника (-1 = любой)",left,131,half,"-1").setTooltip(Tooltip.create(Component.literal("Для партии из нескольких слотов укажи -1. Плата объёма: до 256 предметов, отдельные безопасные билеты.")));field("destinationSlot","Слот назначения (-1 = любой)",left+half+12,131,half,"-1");}
        button("Отправить",left,156,half,()->{if((fluidMode?selectedFluid():selectedItem())==null){status="Выбери груз из содержимого источника";return;}send(fluidMode?"transferFluid":"transfer",form());}).active=selected!=null&&!busy;
        int tool=(half-6)/2;button("Обновить",left+half+12,156,tool,()->requestInventory(false));button(fluidMode?"Жидкости":"Предметы",left+half+18+tool,156,tool,()->{fluidMode=!fluidMode;rebuild();}).setTooltip(Tooltip.create(Component.literal("Переключить тип груза: предметы / жидкости")));
        var receivers=array("receivers");if(!receivers.isEmpty()){var receiver=receivers.get(Math.floorMod(receiverPage,receivers.size())).getAsJsonObject();button("Получатель: "+receiver.get("name").getAsString(),left+half+12,181,half-25,()->{set("destination",receiver.get("ip").getAsString());rebuild();});button(">",left+w-22,181,22,()->{receiverPage++;rebuild();});}
    }
    private void requestInventory(boolean open){
        if(busy){status="Дождись завершения текущего сетевого запроса";return;}
        if(value("source","").isBlank()){openNodeDropdown("source");return;}
        openItemsAfterInspect=open;state.remove("snapshot");send("inspect",form());rebuild();
    }
    private void chooseSource(String ip){
        if(busy){status="Дождись завершения текущего сетевого запроса";return;}
        set("source",ip);set("item","");set("fluid","");values.remove("fluidHash");set("sourceSlot","-1");requestInventory(true);
    }
    private void openNodeDropdown(String key){
        int half=(w-12)/2;openNodeDropdown(key,key.equals("source")?left:left+half+12,85,half);
    }
    private void openNodeDropdown(String key,int x,int y,int width){
        pickerX=x;pickerY=y;pickerWidth=width;pickerTab=tab;
        var options=new ArrayList<ManagementDropdown.Entry>();
        for(var entry:array("endpoints")){
            var node=entry.getAsJsonObject();String ip=node.get("ip").getAsString();boolean allowed=node.get(key.equals("source")?"extract":"insert").getAsBoolean();
            if(key.equals("crafter"))allowed=node.get("extract").getAsBoolean()&&node.get("insert").getAsBoolean()&&node.get("control").getAsBoolean()&&node.has("block")&&node.get("block").getAsString().equals("minecraft:crafting_table");
            if(!allowed)continue;
            options.add(new ManagementDropdown.Entry(node.get("name").getAsString()+" | "+ip,key.equals("source")?"Источник • извлечение разрешено":key.equals("crafter")?"Верстак • крафт разрешён":"Получатель • вставка разрешена",ItemStack.EMPTY,()->{if(key.equals("source")&&tab==1)chooseSource(ip);else{set(key,ip);rebuild();}}));
        }
        int page=state.has("page")?state.get("page").getAsInt():0,pages=state.has("pages")?state.get("pages").getAsInt():1;
        Runnable previous=page>0?()->pickerPage(key,-1):null,next=page+1<pages?()->pickerPage(key,1):null;
        dropdown=new ManagementDropdown(font,x,y,width,height,key.equals("source")?"Источники — имя | IP":key.equals("crafter")?"Верстаки — имя | IP":"Получатели — имя | IP",options,(page+1)+" / "+pages,previous,next,()->dropdown=null);
    }
    private void pickerPage(String key,int direction){if(busy){status="Дождись завершения текущего сетевого запроса";return;}dropdown=null;reopenDropdown=key;networkPage(direction);}
    private void openItemDropdown(){
        if(fluidMode&&tab==1){openFluidDropdown();return;}
        remember();if(busy){status="Дождись завершения текущего сетевого запроса";return;}
        if(!matchingSnapshot()){requestInventory(true);return;}
        var options=new ArrayList<ManagementDropdown.Entry>();
        for(var entry:inventory()){
            var cell=entry.getAsJsonObject();if(cell.get("count").getAsInt()<=0)continue;
            String id=cell.get("item").getAsString();int count=cell.get("count").getAsInt();
            options.add(new ManagementDropdown.Entry(itemName(cell)+" × "+count,"Слот "+cell.get("slot").getAsString()+" • "+id,icon(id),()->{
                set("item",id);set("sourceSlot",cell.get("slot").getAsString());int amount;try{amount=Integer.parseInt(value("count","16"));}catch(NumberFormatException e){amount=16;}set("count",Integer.toString(Math.clamp(amount,1,Math.min(64,count))));rebuild();
            }));
        }
        dropdown=new ManagementDropdown(font,left,118,w-88,height,"Предметы — "+nodeLabel(value("source","")),options,"",null,null,()->dropdown=null);
    }
    private void openFluidDropdown(){remember();if(busy){status="Дождись завершения текущего сетевого запроса";return;}if(!matchingSnapshot()){requestInventory(true);return;}var options=new ArrayList<ManagementDropdown.Entry>();for(var row:tanks()){var tank=row.getAsJsonObject();if(tank.get("amount").getAsInt()<=0||tank.get("drainable").getAsInt()<=0)continue;String id=tank.get("fluid").getAsString();options.add(new ManagementDropdown.Entry(tank.get("name").getAsString()+" / "+tank.get("amount").getAsString()+" mB","Бак "+tank.get("tank").getAsString()+" • "+id,fluidIcon(id),()->selectFluid(tank)));}dropdown=new ManagementDropdown(font,left,118,w-88,height,"Жидкости — "+nodeLabel(value("source","")),options,"",null,null,()->dropdown=null);}
    private void pattern(){var q=form();var grid=new JsonArray();for(int i=0;i<9;i++)grid.add(value("grid"+i,"-"));q.add("grid",grid);send("patternAdd",q);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){}
    @Override public void render(GuiGraphics g,int x,int y,float delta){
        g.fill(0,0,width,height,0xff10232e);if(token.isEmpty())g.drawCenteredString(font,title,width/2,10,0xffd8f2ed);else{g.drawString(font,font.plainSubstrByWidth(title.getString(),w-110),left,8,0xffd8f2ed,false);if(state.has("hardware")){var h=state.getAsJsonObject("hardware");String info=String.format(Locale.ROOT,"%.1f °C | кулер: %s | до %d шт. | %.1f с",h.get("temperature").getAsDouble(),h.get("cooler").getAsString(),h.get("itemLimit").getAsInt(),h.get("offerSeconds").getAsDouble());g.drawString(font,font.plainSubstrByWidth(info,w-110),left,20,h.get("overheated").getAsBoolean()?0xffff7777:0xffa2cfce,false);}}super.render(g,x,y,delta);
        for(var label:labels)g.drawString(font,label[0],Integer.parseInt(label[1]),Integer.parseInt(label[2]),0xffa2cfce,false);
        g.drawWordWrap(font,Component.literal(font.plainSubstrByWidth(status,w*3)),left,height-53,w,0xffffd39b);
        if(tab==4&&state.has("audit")){String hash=state.get("audit").getAsString();g.drawString(font,"SHA-256: "+hash.substring(0,Math.min(24,hash.length()))+"…",left,height-96,0xff8ccbbb,false);}
        if(tab==1&&!token.isEmpty()&&!array("receivers").isEmpty())try{
            var receiver=array("receivers").get(Math.floorMod(receiverPage,array("receivers").size())).getAsJsonObject();var id=UUID.fromString(receiver.get("owner").getAsString());var info=minecraft.getConnection().getPlayerInfo(id);var skin=info==null?net.minecraft.client.resources.DefaultPlayerSkin.get(id):info.getSkin();net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,skin.texture(),left+w-18,205,16);
        }catch(RuntimeException ignored){}
        if(tab==1&&!token.isEmpty()){var selected=fluidMode?selectedFluid():selectedItem();if(selected!=null)g.renderItem(fluidMode?fluidIcon(selected.get("fluid").getAsString()):icon(selected.get("item").getAsString()),left+5,99);}
        for(var icon:choiceIcons)g.renderItem(icon.stack(),icon.x(),icon.y());
        if(dropdown!=null)dropdown.render(g,x,y,delta);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void resize(net.minecraft.client.Minecraft mc,int width,int height){remember();super.resize(mc,width,height);}
    private void networkPage(int direction){int page=state.has("page")?state.get("page").getAsInt():0;var q=new JsonObject();q.addProperty("page",Math.clamp(page+direction,0,63));send("refresh",q);}
    private void journalPage(int direction){int page=state.has("jobPage")?state.get("jobPage").getAsInt():0;var q=new JsonObject();q.addProperty("jobPage",Math.clamp(page+direction,0,31));rowPage=0;send("refresh",q);}
    @Override public void onClose(){PacketDistributor.sendToServer(new ManagementPackets.Request(pc,token,"logout","{}"));minecraft.setScreen(parent);}
    @Override public boolean mouseClicked(double x,double y,int button){return dropdown!=null?dropdown.mouseClicked(x,y,button):super.mouseClicked(x,y,button);}
    @Override public boolean mouseReleased(double x,double y,int button){return dropdown!=null?dropdown.mouseReleased(x,y,button):super.mouseReleased(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return dropdown!=null?dropdown.mouseDragged(x,y,button,dx,dy):super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return dropdown!=null?dropdown.mouseScrolled(dy):super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean charTyped(char ch,int mods){return dropdown!=null?dropdown.charTyped(ch,mods):super.charTyped(ch,mods);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(dropdown!=null)return dropdown.keyPressed(key,scan,mods);if(key!=256)for(var field:fields.values())if(field.isFocused()){if(field.keyPressed(key,scan,mods))return true;if(minecraft.options.keyInventory.matches(key,scan))return true;}return super.keyPressed(key,scan,mods);}
}
