package dev.netlab.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import dev.netlab.core.GuideSearch;
import java.util.*;

/** Searchable contents on the left, paginated reading area on the right. */
public final class GuideBookScreen extends Screen {
    private List<GuideBookContents.Section> sections=List.of();
    private List<GuideBookContents.Article> filtered=List.of();
    private final List<Button> contentsButtons=new ArrayList<>();
    private GuideBookContents.Article article;
    private List<FormattedCharSequence> lines=List.of();
    private int section,page,listOffset,left,top,bookWidth,bookHeight,sidebar,textLeft,textTop,rows,perPage,contentsTop;
    private String query="",lastNotified="";private int heartbeat;
    private Button previous,next;private EditBox searchField;
    public GuideBookScreen(){super(Component.translatable("item.netlab.cisco_bible"));}
    public GuideBookScreen(String sectionId){this();requestedSection=sectionId;}
    private String requestedSection;
    public void refreshContents(){
        if(minecraft==null)return;String selected=sections.isEmpty()?"":sections.get(section).id();var merged=new LinkedHashMap<String,GuideBookContents.Section>();sections.forEach(s->merged.put(s.id(),s));
        for(var provider:GuideBookContents.LIVE)for(var value:provider.get())merged.put(value.id(),value);
        var updated=merged.values().stream().sorted(Comparator.comparingInt(GuideBookContents.Section::order).thenComparing(GuideBookContents.Section::id)).toList();
        boolean tabsChanged=!updated.stream().map(GuideBookContents.Section::id).toList().equals(sections.stream().map(GuideBookContents.Section::id).toList());sections=updated;
        for(int i=0;i<sections.size();i++)if(sections.get(i).id().equals(selected))section=i;
        if(tabsChanged){boolean focus=getFocused()==searchField;int cursor=searchField==null?0:searchField.getCursorPosition();rebuildWidgets();if(focus){setFocused(searchField);searchField.setCursorPosition(cursor);}}else{filter();rebuildContents();}
    }
    @Override protected void init(){
        sections=GuideBookContents.load(minecraft.getResourceManager());section=Math.clamp(section,0,Math.max(0,sections.size()-1));
        if(requestedSection!=null){for(int i=0;i<sections.size();i++)if(sections.get(i).id().equals(requestedSection))section=i;requestedSection=null;}
        bookWidth=Math.min(820,width-12);bookHeight=height-12;left=(width-bookWidth)/2;top=6;sidebar=Math.clamp(bookWidth/3,100,196);
        int columns=Math.max(1,(bookWidth-16)/86),tabRows=(sections.size()+columns-1)/columns;
        contentsTop=top+81+Math.max(0,tabRows-1)*22;
        textLeft=left+sidebar+16;textTop=contentsTop+19;rows=Math.max(1,(top+bookHeight-38-contentsTop)/22);perPage=Math.max(1,(top+bookHeight-34-textTop)/12);
        addRenderableWidget(Button.builder(Component.literal("×"),b->onClose()).bounds(left+bookWidth-26,top+4,20,20).build());
        int tabWidth=(bookWidth-16)/Math.max(1,Math.min(columns,sections.size()));
        for(int i=0;i<sections.size();i++){final int index=i;String label=sections.get(i).title();addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label,tabWidth-8)),b->{section=index;query="";article=null;page=0;listOffset=0;rebuildWidgets();}).bounds(left+8+(i%columns)*tabWidth,top+29+(i/columns)*22,tabWidth-4,20).tooltip(Tooltip.create(Component.literal(label))).build());}
        var search=searchField=addRenderableWidget(new EditBox(font,left+8,contentsTop-27,bookWidth-16,20,Component.literal("Поиск по всей книге")));
        search.setMaxLength(100);search.setHint(Component.literal("Поиск по всей книге…"));search.setValue(query);
        search.setResponder(value->{query=value;listOffset=0;filter();rebuildContents();notifyOpen();});
        previous=addRenderableWidget(Button.builder(Component.literal("◀"),b->{page=Math.max(0,page-1);updateNavigation();}).bounds(textLeft,top+bookHeight-26,24,20).build());
        next=addRenderableWidget(Button.builder(Component.literal("▶"),b->{page=Math.min(pageCount()-1,page+1);updateNavigation();}).bounds(left+bookWidth-34,top+bookHeight-26,24,20).build());
        filter();rebuildContents();notifyOpen();
    }
    private void notifyOpen(){if(article!=null&&!article.id().equals(lastNotified)){lastNotified=article.id();for(var listener:GuideBookContents.OPENED)listener.accept(article.id());}}
    @Override public void tick(){super.tick();if(++heartbeat%200==0)for(var listener:GuideBookContents.TICKED)listener.run();}
    private void filter(){
        if(sections.isEmpty()){filtered=List.of();article=null;paginate();return;}
        String q=query.strip();
        filtered=(q.isBlank()?sections.get(section).articles().stream():sections.stream().flatMap(s->s.articles().stream())).filter(a->GuideSearch.contains(a.title()+"\n"+a.body(),q)).toList();
        if(article==null||filtered.stream().noneMatch(a->a.id().equals(article.id()))){article=filtered.isEmpty()?null:filtered.getFirst();page=0;}
        if(article!=null)article=filtered.stream().filter(a->a.id().equals(article.id())).findFirst().orElse(article);
        paginate();
    }
    private void paginate(){
        var result=new ArrayList<FormattedCharSequence>();
        if(article!=null)for(String paragraph:article.body().split("\n",-1)){
            if(paragraph.isEmpty())result.add(FormattedCharSequence.EMPTY);else result.addAll(font.split(highlight(paragraph),Math.max(40,left+bookWidth-14-textLeft)));
        }
        lines=List.copyOf(result);page=Math.clamp(page,0,pageCount()-1);updateNavigation();
    }
    private Component highlight(String text){return GuideSearchText.highlight(text,query);}
    private int pageCount(){return Math.max(1,(lines.size()+perPage-1)/Math.max(1,perPage));}
    private void updateNavigation(){if(previous!=null)previous.active=page>0;if(next!=null)next.active=page+1<pageCount();}
    private void rebuildContents(){
        for(var b:contentsButtons)removeWidget(b);contentsButtons.clear();listOffset=Math.clamp(listOffset,0,Math.max(0,filtered.size()-rows));
        for(int i=listOffset;i<Math.min(filtered.size(),listOffset+rows);i++){var entry=filtered.get(i);String label=(article!=null&&article.id().equals(entry.id())?"› ":"")+entry.title();
            var button=Button.builder(Component.literal(font.plainSubstrByWidth(label,sidebar-14)),b->{article=entry;page=0;paginate();rebuildContents();notifyOpen();}).bounds(left+8,contentsTop+(i-listOffset)*22,sidebar-4,20).tooltip(Tooltip.create(Component.literal(entry.title()))).build();
            contentsButtons.add(addRenderableWidget(button));
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(x<textLeft){listOffset-=(int)(dy*2);rebuildContents();}else{page=Math.clamp(page-(int)Math.signum(dy),0,pageCount()-1);updateNavigation();}return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!(getFocused() instanceof EditBox)&&(key==GLFW.GLFW_KEY_PAGE_DOWN||key==GLFW.GLFW_KEY_PAGE_UP)){page=Math.clamp(page+(key==GLFW.GLFW_KEY_PAGE_DOWN?1:-1),0,pageCount()-1);updateNavigation();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    @Override public void render(GuiGraphics g,int x,int y,float dt){
        g.fill(0,0,width,height,0xe0080d15);g.fill(left-2,top-2,left+bookWidth+2,top+bookHeight+2,0xff584029);g.fill(left,top,left+bookWidth,top+bookHeight,0xffe9d9b3);
        g.fill(left+sidebar+9,contentsTop-2,left+sidebar+10,top+bookHeight-8,0xffb8a17a);
        g.drawString(font,title,left+(bookWidth-font.width(title))/2,top+9,0xff3c2b1e,false);
        if(article!=null){g.drawString(font,highlight(font.plainSubstrByWidth(article.title(),left+bookWidth-textLeft-12)),textLeft,contentsTop+3,0xff3c2b1e,false);
            for(int i=page*perPage;i<Math.min(lines.size(),(page+1)*perPage);i++)g.drawString(font,lines.get(i),textLeft,textTop+(i-page*perPage)*12,0xff332b23,false);
        }else g.drawString(font,"Ничего не найдено",textLeft,textTop,0xff665340,false);
        g.drawCenteredString(font,"Стр. "+(page+1)+" / "+pageCount(),(textLeft+left+bookWidth)/2,top+bookHeight-20,0xff59452e);
        g.drawString(font,"Статей: "+filtered.size(),left+10,top+bookHeight-20,0xff59452e,false);
        super.render(g,x,y,dt);
    }
    @Override public boolean isPauseScreen(){return false;}
}
