/*
 * This file is part of Hearthborn Client.
 *
 * Copyright (C) 2026 Jagy2014
 * GitHub: https://github.com/Jagy2014
 *
 * Licensed under the GNU Lesser General Public License
 * version 3.0 (LGPL-3.0).
 *
 * See the COPYING file for details.
 */

package hearthborn.inventory;

import haven.*;
import hearthborn.L10n;
import hearthborn.QualityOverlay;
import hearthborn.l10n.LocalizedText;
import hearthborn.ui.UiTheme;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A live inventory view with selection, row gestures and explicit discard previews.
 *
 * @author Jagy2014
 */
public final class ContainerOverview extends Widget {
    private static final Map<Inventory,ContainerOverview> open = new WeakHashMap<>();
    private static final Pattern SEED_COUNT=Pattern.compile("^(.*?)\\s*\\(\\s*[0-9]+\\s+(?:seeds?|семян|семя|семени)\\s*\\)$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private final Inventory source;
    private final TextEntry search;
    private final SListBox<Row,Widget> list;
    private final Label status;
    private final Label nameHeader,quantityHeader,qualityHeader;
    private OverviewDialog dialog;
    private OverviewMenu menu;
    private enum Sort {NAME, QUANTITY, QUALITY}
    private Sort sort=Sort.NAME;
    private boolean descending;
    private final java.text.Collator names=java.text.Collator.getInstance(Locale.forLanguageTag(L10n.language()));
    private final Set<String> expanded=new HashSet<>();
    private final Map<WItem,Integer> ownerIds=new WeakHashMap<>();
    private int nextOwnerId;
    private List<Row> rows=Collections.emptyList();
    private String selected,hovered,lastClick,result="";
    private boolean loading,ownTransfer;
    private double elapsed,lastClickAt,resultUntil;
    private int heightLimit=UI.scale(520),totalItems,totalTypes;
    private final Footer footer;

    static final class Portion {
        final WItem owner;
        final GItem item;
        final double quality;
        final int count;
        Portion(WItem owner,GItem item) {
            this.owner=owner;this.item=item;
            Double q=QualityOverlay.qualityOf(item);
            quality=q==null?-1:q;
            int amount=Math.max(1,item.num);
            for(ItemInfo tip:item.info())if(tip instanceof GItem.Amount)amount=Math.max(1,((GItem.Amount)tip).itemnum());
            count=amount;
        }
        boolean live(Inventory inventory) {return owner.parent==inventory&&(item==owner.item||item.hasparent(owner.item.contents));}
    }
    static final class Group {
        final String key,name,english;
        final List<Portion> portions=new ArrayList<>();
        private final List<WItem> ownerItems=new ArrayList<>();
        private final Set<WItem> seenOwners=Collections.newSetFromMap(new IdentityHashMap<>());
        private int stackCount;
        int count,known;
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,total;
        Group(String key,String english,String name) {
            this.key=key;this.english=english;this.name=name;
        }
        void add(Portion portion) {
            portions.add(portion);count+=portion.count;
            if(seenOwners.add(portion.owner)) {
                ownerItems.add(portion.owner);
                if(QualityOverlay.stacked(portion.owner.item))stackCount++;
            }
            if(portion.quality>=0) {known+=portion.count;total+=portion.quality*portion.count;min=Math.min(min,portion.quality);max=Math.max(max,portion.quality);}
        }
        String exactRange() {return known==0?"?":String.format(Locale.ROOT,Double.compare(min,max)==0?"%.2f":"%.2f–%.2f",min,max)+(known<count?" / ?":"");}
        String average() {return known==0?"?":QualityOverlay.format(total/known)+(known<count?" / ?":"");}
        List<WItem> owners() {return ownerItems;}
        int stacks() {return stackCount;}
        String summary() {int stacks=stacks();return L10n.format("%d items",count)+(stacks>0?" · "+L10n.format("%d stacks",stacks):"");}
        String tip() {return name+"\n"+summary()+"\n"+L10n.tr("Quality")+": "+exactRange()+(known>0?"\n"+L10n.tr("Average quality")+": "+String.format(Locale.ROOT,"%.2f",total/known):"");}
        boolean wholeStacks() {
            Set<GItem> members=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Portion portion:portions)members.add(portion.item);
            for(WItem owner:owners())if(!members.contains(owner.item)&&owner.item.contents!=null)
                for(GItem member:owner.item.contents.children(GItem.class))if(!members.contains(member))return false;
            return true;
        }
        Map<Double,Group> qualities() {
            Map<Double,Group> result=new TreeMap<>(Comparator.reverseOrder());
            for(Portion portion:portions)result.computeIfAbsent(portion.quality,q->new Group(key+"\nq:"+q,english,name)).add(portion);
            return result;
        }
    }
    static final class Snapshot {
        final List<Group> groups=new ArrayList<>();
        boolean loading;
    }
    static Snapshot snapshot(Inventory inventory) {
        Snapshot result=new Snapshot();Map<String,Group> groups=new LinkedHashMap<>();
        for(WItem view:InventoryActions.items(inventory))try {
            List<GItem> pieces=new ArrayList<>();
            if(QualityOverlay.stacked(view.item)&&view.item.contents!=null)pieces.addAll(view.item.contents.children(GItem.class));
            if(pieces.isEmpty())pieces.add(view.item);
            // Resolve every member of this icon before publishing its counts.
            List<Portion> ready=new ArrayList<>();List<String> names=new ArrayList<>(),resources=new ArrayList<>(),labels=new ArrayList<>();
            for(GItem item:pieces) {
                ItemInfo.Name title=ItemInfo.find(ItemInfo.Name.class,item.info());
                if(title==null)title=ItemInfo.find(ItemInfo.Name.class,view.item.info());
                if(title==null)throw new Loading();
                String name=title.str.text.replaceFirst(", stack of$","");
                // Dynamic names (meat species, filet preparation, etc.) are more specific than the base resource.
                String label=LocalizedText.shown(title.str).replaceFirst(", stack of$","");
                String resource=item.resource().name;
                Matcher seeds=SEED_COUNT.matcher(name),shownSeeds=SEED_COUNT.matcher(label);
                boolean counted=seeds.matches(),shownCounted=shownSeeds.matches();
                if(counted||shownCounted) {
                    String crop=counted?seeds.group(1).trim():name;
                    String seedResource=resource.startsWith("gfx/invobjs/seed-")?resource
                        :"gfx/invobjs/seed-"+resource.substring(resource.lastIndexOf('/')+1);
                    String translated=L10n.find("tooltip",seedResource);
                    Resource.Tooltip base=item.resource().layer(Resource.tooltip);
                    // Resource sprites can compose a name from an already translated tooltip.
                    boolean specific=base!=null&&!base.original.trim().isEmpty()&&!base.original.equalsIgnoreCase("Seed")&&!base.original.equalsIgnoreCase("Seeds");
                    if(specific&&(resource.startsWith("gfx/invobjs/seed-")||(translated!=null&&crop.matches(".*[А-Яа-яЁё].*"))))crop=base.original;
                    crop=crop.replaceFirst("(?i)^Seeds of ","").replaceFirst("(?i) seeds$","").trim();
                    name="Seeds of "+crop;
                    label=translated!=null?translated:L10n.language().equals("en")?name
                        :L10n.format("Seeds: %s",shownCounted?shownSeeds.group(1).trim():LocalizedText.shown(title.str));
                }
                ready.add(new Portion(view,item));names.add(name);resources.add(resource);labels.add(label);
            }
            for(int i=0;i<ready.size();i++) {
                String name=names.get(i),resource=resources.get(i),label=labels.get(i),key=resource+"\n"+name;
                groups.computeIfAbsent(key,k->new Group(k,name,label)).add(ready.get(i));
            }
        }catch(Loading pending) {result.loading=true;}
        result.groups.addAll(groups.values());
        return result;
    }
    static final class Row {
        final String key;
        final boolean detail,individual;
        Group group;
        Row(Group group,boolean detail,boolean individual) {key=group.key;this.group=group;this.detail=detail;this.individual=individual;}
    }

    public ContainerOverview(Inventory source) {
        super(UI.scale(OverviewTheme.WIDTH,440));this.source=source;
        add(new Label("Container overview"));add(new Label("Search:"),UI.scale(0,29));
        search=add(new OverviewTheme.Entry(OverviewTheme.WIDTH-60,""){
            protected void changed(){super.changed();if(list!=null)refresh();}
        },UI.scale(60,24));search.settip("Search items by name");
        names.setStrength(java.text.Collator.PRIMARY);
        nameHeader=add(header("Item",Sort.NAME,"Click to sort by name; click again to reverse."),UI.scale(28,53));
        quantityHeader=add(header("Qty",Sort.QUANTITY,"Click to sort by quantity; click again to reverse."));
        qualityHeader=add(header("Avg. quality",Sort.QUALITY,"Click to sort by average quality; click again to reverse."));
        list=add(new SListBox<Row,Widget>(UI.scale(OverviewTheme.WIDTH,300),UI.scale(OverviewTheme.ROW)){
            protected List<Row> items(){return rows;}
            protected Widget makeitem(Row row,int index,Coord size){return new RowView(row,size);}
            public void change(Row row){super.change(row);selected=row==null?null:row.key;updateStatus();}
            protected boolean unselect(int button){lastClick=null;return super.unselect(button);}
        },UI.scale(0,74));
        status=new Label(""); // Status text is painted by the one-line footer, without extra controls.
        footer=add(new Footer());updateHeaders();open.put(source,this);refresh();
    }
    Inventory source(){return source;}
    boolean usable(){Window w=source.getparent(Window.class);return parent!=null&&source.parent!=null&&w!=null&&w.visible()&&source.tvisible();}
    static String baseKey(String key){int at=key.indexOf("\nq:");return at<0?key:key.substring(0,at);}
    static long doubleClickInterval(){
        try{Object n=java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");if(n instanceof Number)return Math.max(1,((Number)n).longValue());}catch(java.awt.HeadlessException ignored){}
        return 500;
    }
    private static String normalized(String value){return value.toLowerCase(Locale.ROOT).replace('ё','е').trim();}
    private Label header(String title,Sort column,String tip){
        return (Label)new Label(title){public boolean mousedown(MouseDownEvent e){if(e.b!=1)return false;descending=sort==column?!descending:column!=Sort.NAME;sort=column;lastClick=null;updateHeaders();refresh();list.scrollval(0);return true;}}.settip(tip);
    }
    private int qualityRight(){return list.sz.x-(list.sb==null?0:list.sb.sz.x)-UI.scale(OverviewTheme.ACTION+8);}
    private void updateHeaders(){
        nameHeader.settext(L10n.tr("Item")+(sort==Sort.NAME?(descending?" ↓":" ↑"):""));
        quantityHeader.settext(L10n.tr("Qty")+(sort==Sort.QUANTITY?(descending?" ↓":" ↑"):""));
        qualityHeader.settext(L10n.tr("Avg. quality")+(sort==Sort.QUALITY?(descending?" ↓":" ↑"):""));
        quantityHeader.move(Coord.of(UI.scale(OverviewTheme.WIDTH-150)-quantityHeader.sz.x,UI.scale(53)));
        qualityHeader.move(Coord.of(qualityRight()-qualityHeader.sz.x,UI.scale(53)));
    }
    private Comparator<Group> order(){
        Comparator<Group> byName=(a,b)->names.compare(a.name,b.name);
        Comparator<Group> order=sort==Sort.QUANTITY?Comparator.comparingInt(g->g.count):sort==Sort.QUALITY?Comparator.comparingDouble(g->g.known==0?0:g.total/g.known):byName;
        if(descending)order=order.reversed();
        if(sort==Sort.QUALITY)order=Comparator.comparingInt((Group g)->g.known==0?1:0).thenComparing(order);
        return order.thenComparing(byName).thenComparing(g->g.key);
    }
    void refresh(){
        Snapshot snapshot=snapshot(source);loading=snapshot.loading;totalItems=0;totalTypes=snapshot.groups.size();
        for(Group g:snapshot.groups)totalItems+=g.count;snapshot.groups.sort(order());
        Map<String,Row> previous=new HashMap<>();for(Row row:rows)previous.put(row.key,row);
        List<Row> next=new ArrayList<>();String query=normalized(search.text());
        for(Group group:snapshot.groups)if(query.isEmpty()||normalized(group.name+" "+group.english).contains(query)){
            append(next,previous,group,false,false);
            if(expanded.contains(group.key)){
                List<Group> qualities=new ArrayList<>(group.qualities().values());
                boolean individual=qualities.size()==1;
                if(individual)qualities=individuals(qualities.get(0));
                if(sort!=Sort.NAME)qualities.sort(order());
                for(Group quality:qualities)append(next,previous,quality,true,individual);
            }
        }
        rows=next;Row chosen=selected();if(chosen==null)selected=null;list.sel=chosen;
        if(find(hovered)==null)hovered=null;updateStatus();fitHeight(heightLimit);
    }
    private List<Group> individuals(Group group){
        Map<WItem,Group> items=new LinkedHashMap<>();
        for(Portion portion:group.portions)items.computeIfAbsent(portion.owner,owner->{
            int id=ownerIds.computeIfAbsent(owner,key->++nextOwnerId);
            return new Group(group.key+"\nowner:"+id,group.english,group.name);
        }).add(portion);
        return new ArrayList<>(items.values());
    }
    private void append(List<Row> next,Map<String,Row> previous,Group group,boolean detail,boolean individual){Row row=previous.get(group.key);if(row==null)row=new Row(group,detail,individual);else row.group=group;next.add(row);}
    Row find(String key){if(key!=null)for(Row row:rows)if(row.key.equals(key))return row;return null;}
    private Row selected(){return find(selected);}
    private String summary(Group group){return L10n.format("%s · %d items · %d stacks · avg. %s",group.name,group.count,group.stacks(),group.average());}
    private void updateStatus(){
        Row row=find(hovered);if(row==null)row=selected();
        String text=Utils.rtime()<resultUntil?result:loading?L10n.tr("Some items are still loading."):row!=null?summary(row.group):L10n.format("Total: %d items · %d types",totalItems,totalTypes);
        status.settext(text);
    }
    private void result(String text){result=text;resultUntil=Utils.rtime()+2.75;updateStatus();}
    void transferred(int count,int requested,double min,double max,boolean full,boolean gesture){
        ownTransfer=false;if(parent==null)return;
        if(count>0&&gesture)OverviewSettings.success();
        result(full?L10n.format("Backpack full: took %d of %d",count,requested):count>0&&Double.isFinite(min)?L10n.format("Taken: %d items (Q %s–%s)",count,QualityOverlay.format(min),QualityOverlay.format(max)):L10n.format("Taken: %d items",count));
        refresh();
    }
    void discarded(ContainerDiscard.Plan plan){
        if(parent==null)return;
        pendingDiscard=plan;discardDeadline=Utils.rtime()+8;
    }
    private ContainerDiscard.Plan pendingDiscard;private double discardDeadline;
    Inventory destination(){GameUI gui=source.getparent(GameUI.class);return gui==null?null:source==gui.maininv?BagTransfers.storage(gui,source):gui.maininv;}
    Row actionRow(String key){
        refresh();GameUI gui=source.getparent(GameUI.class);
        if(loading||!usable()||gui==null||!InventoryActions.cursorFree(source))return null;
        if(BagTransfers.running(gui)||pendingDiscard!=null){result(L10n.tr("Wait for the current transfer to finish."));return null;}
        Row row=find(key);if(row!=null&&!loaded(row)){result(L10n.tr("Stack contents are still loading."));return null;}return row;
    }
    boolean discarding(){return pendingDiscard!=null;}
    static boolean loaded(Row row){for(WItem owner:row.group.owners())if(QualityOverlay.stacked(owner.item)&&(owner.item.contents==null||owner.item.contents.children(GItem.class).isEmpty()))return false;return true;}
    void move(String key,int amount,boolean worst,boolean gesture){
        Row row=actionRow(key);if(row==null)return;
        if(amount==Integer.MAX_VALUE){moveWhole(row,gesture);return;}
        ownTransfer=OverviewTransfer.start(this,row,amount,worst,gesture);
        if(!ownTransfer)result(L10n.format("Taken: %d items",0));
    }
    private void moveWhole(Row row,boolean gesture){
        Inventory target=destination();if(target==null){result(L10n.tr("Open a destination container first."));return;}
        if(!row.group.wholeStacks()){result(L10n.tr("Select the item group to transfer a stack with mixed qualities."));return;}
        ownTransfer=OverviewTransfer.startWhole(this,row,gesture);
    }
    int previewTake(Row row,int amount,boolean worst){
        Inventory target=destination();if(target==null)return 0;
        boolean[][] used=new boolean[target.isz.y][target.isz.x];
        for(int y=0;y<target.isz.y;y++)for(int x=0;x<target.isz.x;x++)used[y][x]=target.sqmask!=null&&target.sqmask[y*target.isz.x+x];
        for(WItem item:InventoryActions.items(target))occupy(used,InventoryActions.cell(item),item.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz));
        int count=0;for(Portion p:OverviewTransfer.ordered(row.group,amount,worst)){
            Coord size=p.owner.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);boolean fits=false;
            for(int y=0;y+size.y<=used.length&&!fits;y++)for(int x=0;x+size.x<=target.isz.x&&!fits;x++){
                boolean free=true;for(int yy=y;yy<y+size.y;yy++)for(int xx=x;xx<x+size.x;xx++)if(used[yy][xx])free=false;
                if(free){occupy(used,Coord.of(x,y),size);count+=p.count;fits=true;}
            }
        }return count;
    }
    private static void occupy(boolean[][] used,Coord at,Coord size){for(int y=Math.max(0,at.y);y<Math.min(used.length,at.y+size.y);y++)for(int x=Math.max(0,at.x);x<Math.min(used[y].length,at.x+size.x);x++)used[y][x]=true;}
    void input(String key,OverviewDialog.Mode mode,boolean worst,Coord at){if(dialog!=null)dialog.dismiss();dialog=OverviewDialog.open(this,key,mode,worst,at);}
    void dropGroup(String key,boolean selection,Coord at){
        if(!selection&&!OverviewSettings.confirm()){
            Row row=actionRow(key);if(row!=null){ContainerDiscard.Plan plan=ContainerDiscard.Plan.make(row.group,null);OverviewDialog.execute(this,plan);}return;
        }
        Row row=find(key);
        input(key,selection?(row!=null&&row.individual?OverviewDialog.Mode.DROP_ITEM:OverviewDialog.Mode.DROP_SELECTION):OverviewDialog.Mode.DROP_GROUP,false,at);
    }
    // Kept as programmatic entry points; visible actions are exclusively row gestures and menus.
    void take(boolean worst){Row row=selected();if(row!=null)move(row.key,1,worst,false);}
    void transfer(){Row row=selected();if(row!=null)move(row.key,Integer.MAX_VALUE,false,false);}
    void discard(boolean below){Row row=selected();if(row!=null)input(row.key,below?OverviewDialog.Mode.DROP_BELOW:OverviewDialog.Mode.DROP_GROUP,false,ui.mc);}
    public void draw(GOut g){UiTheme.panel(g,sz);super.draw(g);}
    public void tick(double dt){
        super.tick(dt);if(tvisible()&&(elapsed+=dt)>=.15){elapsed=0;refresh();}
        if(pendingDiscard!=null){boolean present=false;for(Portion p:pendingDiscard.members)if(p.live(source))present=true;
            if(!present){result(L10n.format("Dropped: %d items",pendingDiscard.count));pendingDiscard=null;refresh();}
            else if(Utils.rtime()>discardDeadline){pendingDiscard=null;result(L10n.tr("The discard did not finish. Check the container."));}}
    }
    void fitHeight(int available){
        heightLimit=available;int height=Math.max(UI.scale(150),Math.min(UI.scale(102+OverviewTheme.ROW*Math.max(5,Math.min(18,rows.size()))),available));
        int listHeight=Math.max(UI.scale(OverviewTheme.ROW),height-UI.scale(102));
        if(list.sz.y!=listHeight)list.resize(Coord.of(list.sz.x,listHeight));
        footer.move(Coord.of(0,list.c.y+list.sz.y));resize(Coord.of(sz.x,footer.c.y+footer.sz.y));
    }
    public void dispose(){if(menu!=null)menu.close();if(dialog!=null)dialog.dismiss();if(ownTransfer)BagTransfers.cancel(source.getparent(GameUI.class));if(open.get(source)==this)open.remove(source);status.dispose();super.dispose();}
    public static boolean selected(WItem item){
        if(!(item.parent instanceof Inventory))return false;ContainerOverview overview=open.get((Inventory)item.parent);if(overview==null||!overview.tvisible())return false;
        Row row=overview.selected();if(row==null)return false;for(Portion p:row.group.portions)if(p.owner==item&&p.live(overview.source))return true;return false;
    }
    public static void highlight(GOut g,WItem item){if(selected(item)){g.chcolor(255,213,105,35);g.frect(Coord.z,item.sz);g.chcolor(OverviewTheme.GOLD);for(int i=0;i<UI.scale(2);i++)g.rect(Coord.of(i,i),item.sz.sub(i*2,i*2));g.chcolor();}}
    private final class Footer extends Widget {
        private final Text.Foundry small=new Text.Foundry(Text.sans,10).aa(true);
        Footer(){super(UI.scale(OverviewTheme.WIDTH,OverviewTheme.FOOTER));settip(gestures());}
        // The key legend belongs to the "?" in the corner; it never covers an open menu.
        public Object tooltip(Coord at,Widget previous){return menuOpen()||at.x<sz.x-UI.scale(24)?null:gestures();}
        public void draw(GOut g){
            updateStatus();int help=UI.scale(20),width=sz.x-help;
            Text left=Text.std.render(status.texts,OverviewTheme.ink());
            Coord textSize=left.sz();if(textSize.x>width)textSize=Coord.of(width,Math.max(1,(int)Math.round(textSize.y*width/(double)textSize.x)));
            g.aimage(left.tex(),Coord.of(0,sz.y/2),0,.5,textSize);
            int x=textSize.x+UI.scale(10);left.dispose();
            if(OverviewSettings.hints()&&x<width){Text hint=small.render(OverviewTheme.fit(L10n.tr("2×LMB — backpack · Shift+LMB/RMB — best/worst"),width-x,small),OverviewTheme.muted());g.aimage(hint.tex(),Coord.of(x,sz.y/2),0,.5);hint.dispose();}
            g.chcolor(OverviewTheme.muted());g.atext("?",Coord.of(sz.x-UI.scale(8),sz.y/2),.5,.5);g.chcolor();
        }
    }
    /** The context menu is the only hint while it is open. */
    private boolean menuOpen(){return menu!=null&&menu.parent!=null;}
    private static String gestures(){return L10n.tr("LMB: select · Double LMB: transfer all\nShift+LMB: take best · Shift+RMB: take worst\nCtrl+Shift+LMB/RMB: take five best/worst\nRMB: actions · Arrow: expand group");}
    private final class RowView extends Widget {
        private final Row row;private boolean hover;
        private Text name,count,quality,arrow,tip;private String caption;private Tex icon;private BufferedImage image;
        RowView(Row row,Coord size){super(size);this.row=row;}
        private void clearText(){if(name!=null)name.dispose();if(count!=null)count.dispose();if(quality!=null)quality.dispose();if(arrow!=null)arrow.dispose();if(tip!=null)tip.dispose();tip=null;}
        private void update(){String next=row.group.tip()+expanded.contains(row.key)+UiTheme.generation();if(next.equals(caption))return;caption=next;clearText();Color ink=OverviewTheme.ink();
            name=Text.render(row.detail&&!row.individual?L10n.format("Q %s",row.group.exactRange()):row.group.name,ink);count=Text.render(Integer.toString(row.group.count),ink);quality=Text.render(row.individual?row.group.exactRange():row.detail?"":row.group.average(),ink);arrow=Text.render(row.detail?"":expanded.contains(row.key)?"▾":"▸",ink);
        }
        public void draw(GOut g){update();if(row.key.equals(selected)||hover){g.chcolor(OverviewTheme.ACCENT);g.frect(Coord.z,sz);g.chcolor();}int y=sz.y/2;
            g.aimage(arrow.tex(),Coord.of(UI.scale(7),y),0,.5);
            if(!row.detail||row.individual)try{GSprite sprite=row.group.portions.get(0).item.spr();if(sprite instanceof GSprite.ImageSprite){BufferedImage next=((GSprite.ImageSprite)sprite).image();if(image!=next){if(icon!=null)icon.dispose();image=next;icon=new TexI(next);}double fit=UI.scale(24)/(double)Math.max(next.getWidth(),next.getHeight());g.image(icon,UI.scale(row.individual?30:20,3),Coord.of(Math.max(1,(int)Math.round(next.getWidth()*fit)),Math.max(1,(int)Math.round(next.getHeight()*fit))));}}catch(Loading ignored){}
            int qty=UI.scale(OverviewTheme.WIDTH-150),right=sz.x-UI.scale(OverviewTheme.ACTION+8),left=UI.scale(row.individual?58:row.detail?30:48);
            g.reclip(Coord.of(left,0),Coord.of(qty-left-UI.scale(14),sz.y)).aimage(name.tex(),Coord.of(0,y),0,.5);
            g.aimage(count.tex(),Coord.of(qty,y),1,.5);g.reclip(Coord.of(qty+UI.scale(8),0),Coord.of(Math.max(1,right-qty-UI.scale(8)),sz.y)).aimage(quality.tex(),Coord.of(Math.max(1,right-qty-UI.scale(8)),y),1,.5);
            if(!row.detail){int x=sz.x-UI.scale(OverviewTheme.ACTION);GOut trash=g.reclip(Coord.of(x,0),UI.scale(OverviewTheme.ACTION,OverviewTheme.ROW));trash.chcolor(hover?OverviewTheme.GOLD:OverviewTheme.muted());trash.rect(UI.scale(9,9),UI.scale(10,13));trash.line(UI.scale(7,7),UI.scale(21,7),UI.scale(1));trash.line(UI.scale(11,4),UI.scale(17,4),UI.scale(1));trash.line(UI.scale(12,12),UI.scale(12,19),UI.scale(1));trash.line(UI.scale(16,12),UI.scale(16,19),UI.scale(1));trash.chcolor();}
        }
        public boolean mousehover(MouseHoverEvent e,boolean hovering){hover=hovering;if(hovering)hovered=row.key;else if(row.key.equals(hovered))hovered=null;updateStatus();return true;}
        public void mousemove(MouseMoveEvent e){hover=e.c.isect(Coord.z,sz);if(hover)hovered=row.key;else if(row.key.equals(hovered))hovered=null;updateStatus();}
        public boolean mousedown(MouseDownEvent e){
            boolean shift=ui.modshift,ctrl=ui.modctrl,other=ui.modmeta||ui.modsuper;
            if(shift&&!other&&(e.b==1||e.b==3)){lastClick=null;ContainerOverview.this.move(row.key,row.individual?Integer.MAX_VALUE:ctrl?5:1,e.b==3,true);return true;}
            if(shift||ctrl||other){lastClick=null;return true;}
            if(!row.detail&&e.b==1&&e.c.x>=sz.x-UI.scale(OverviewTheme.ACTION)){lastClick=null;input(row.key,OverviewDialog.Mode.DROP_BELOW,false,rootpos(e.c));return true;}
            if(e.b==3){lastClick=null;if(actionRow(row.key)==null)return true;if(menu!=null)menu.close();Coord top=rootpos(Coord.z);menu=OverviewMenu.open(ContainerOverview.this,row,rootpos(e.c),top.y,top.y+sz.y);return true;}
            if(e.b!=1)return true;
            if(!row.detail&&e.c.x<UI.scale(20)){lastClick=null;list.change(row);if(!expanded.add(row.key))expanded.remove(row.key);refresh();return true;}
            double now=Utils.rtime();boolean twice=row.key.equals(lastClick)&&now-lastClickAt<=doubleClickInterval()/1000.0;
            list.change(row);lastClick=twice?null:row.key;lastClickAt=now;
            if(twice)ContainerOverview.this.move(row.key,Integer.MAX_VALUE,false,true);return true;
        }
        public Object tooltip(Coord at,Widget previous){if(!row.detail&&at.x>=sz.x-UI.scale(OverviewTheme.ACTION))return L10n.tr("Discard items with quality below X. You will be asked to enter X.");update();return null;}
        public void dispose(){clearText();if(icon!=null)icon.dispose();super.dispose();}
    }
}
