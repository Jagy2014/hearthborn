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

package hearthborn.ui;

import haven.*;
import java.util.*;

/**
 * Replace the server's account badges while retaining its status and hover text.
 *
 * @author Jagy2014
 */
public final class AccountStatusFlag extends Img {
    private static final Map<String,Tex> textures=new HashMap<>();
    public final boolean subscribed;
    public final boolean active;
    public final boolean donation;
    private Tex tip;
    private String serverTip;
    private static final Text.Foundry VERIFIED_TITLE=new Text.Foundry(Text.sans,13).aa(true);
    private static final Text.Foundry BODY=new Text.Foundry(Text.sans,10).aa(true);
    private static final Text.Foundry INVITE=new Text.Foundry(Text.sans,10).aa(true);
    private static final java.awt.Color STATUS_GREEN=new java.awt.Color(57,216,107);
    private static final java.awt.Color STATUS_GOLD=new java.awt.Color(255,191,64);
    private static final String[] MONTHS={"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
    public void uimsg(String message,Object... args){
        if("tip".equals(message)){
            serverTip=args.length>0&&args[0] instanceof String?(String)args[0]:null;
            if(tip!=null){tip.dispose();tip=null;}
        }
        super.uimsg(message,args);
    }
    public static final java.net.URI STORE=java.net.URI.create("https://www.havenandhearth.com/portal/store");
    /** Buy Me a Coffee supports the Hearthborn client and does not ask for a GitHub account. */
    public static final java.net.URI DONATE=java.net.URI.create("https://buymeacoffee.com/hearthborn_client");
    public static boolean matches(String resource){return "gfx/ccver".equals(resource)||"gfx/ccsub".equals(resource);}
    private static synchronized Tex texture(boolean subscribed,boolean active,boolean donation){
        String name=donation?"donation-medal":(subscribed?"subscribed-medal":"verified-seal")+(active?"":"-inactive");
        return textures.computeIfAbsent(name,key->{
            try(java.io.InputStream in=hearthborn.assets.Assets.open("icons/account/"+key.replace("-inactive","")+".png")){
                java.awt.image.BufferedImage source=javax.imageio.ImageIO.read(in);
                Coord size=badgeSize(donation);
                double scale=Math.min(size.x/(double)source.getWidth(),size.y/(double)source.getHeight());
                Coord fitted=Coord.of((int)Math.round(source.getWidth()*scale),(int)Math.round(source.getHeight()*scale));
                java.awt.image.BufferedImage image=TexI.mkbuf(size);
                java.awt.Graphics2D graphics=image.createGraphics();
                graphics.drawImage(PUtils.uiscale(source,fitted),(size.x-fitted.x)/2,(size.y-fitted.y)/2,null);
                graphics.dispose();
                if(!active)for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                    int pixel=image.getRGB(x,y),gray=(((pixel>>16)&255)*30+((pixel>>8)&255)*59+(pixel&255)*11)/100;
                    image.setRGB(x,y,(pixel&0xff000000)|(gray<<16)|(gray<<8)|gray);
                }
                return new TexI(image);
            }catch(java.io.IOException e){throw new RuntimeException("Cannot load account flag",e);}
        });
    }
    public AccountStatusFlag(String resource){this("gfx/ccsub".equals(resource),true,false);}
    public AccountStatusFlag(boolean subscribed,boolean active){this(subscribed,active,false);}
    /** Verification, subscription, and the heart share one size. */
    static Coord badgeSize(boolean donation){return UI.scale(72,72);}
    /** The heart badge is always shown and is not an account status. */
    public static AccountStatusFlag donation(){return new AccountStatusFlag(false,true,true);}
    private AccountStatusFlag(boolean subscribed,boolean active,boolean donation){super(texture(subscribed,active,donation));this.subscribed=subscribed;this.active=active;this.donation=donation;}
    public static boolean hasStatus(UI ui,boolean subscription){
        for(AccountStatusFlag flag:ui.root.children(AccountStatusFlag.class))if(!flag.donation&&flag.active&&flag.subscribed==subscription&&flag.tvisible())return true;
        return false;
    }
    public Object tooltip(Coord at,Widget previous){
        if(tip==null)tip=new TexI(tipImage(subscribed,active,donation,serverTip));
        return tip;
    }
    static java.awt.image.BufferedImage tipImage(boolean subscribed,boolean active,String serverTip){return tipImage(subscribed,active,false,serverTip);}
    static java.awt.image.BufferedImage tipImage(boolean subscribed,boolean active,boolean donation,String serverTip){
        int width=UI.scale(250);
        if(donation)return supportTip(width);
        if(subscribed)return subscriptionTip(active,serverTip,width);
        return verificationTip(active,width);
    }
    /** 2026-11-29 20:07:19 UTC becomes 29 Nov 2026, 20:07 UTC. */
    static String expiration(String serverTip){
        if(serverTip==null)return null;
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})[ T](\\d{2}):(\\d{2})").matcher(serverTip);
        if(!match.find())return null;
        int month=Integer.parseInt(match.group(2));
        if(month<1||month>12)return null;
        return Integer.parseInt(match.group(3))+" "+MONTHS[month-1]+" "+match.group(1)+", "+match.group(4)+":"+match.group(5)+" UTC";
    }
    private static java.awt.image.BufferedImage verificationTip(boolean active,int width){
        if(active)return stack(heading("Haven & Hearth - Account Verified",STATUS_GREEN),
            body("Your account is permanently verified.",width),
            body(width,block("Benefits:","• Extra inventory column","• +20% character development","• +50% convenience","• Exclusive commemorative hat")),
            body("Thank you for supporting Haven & Hearth!",width),
            invite("Click to open the official game store.",width));
        return stack(heading("Haven & Hearth - Account Not Verified",STATUS_GOLD),
            body(width,block("Verify your account to unlock:","• Extra inventory column","• +20% character development","• +50% convenience","• Exclusive commemorative hat")),
            invite("One-time payment. Permanent verification.",width),
            body("Haven & Hearth remains free to play. Verification helps support the official game developers.",width),
            invite("Click to verify your account in the official game store.",width));
    }
    private static java.awt.image.BufferedImage subscriptionTip(boolean active,String serverTip,int width){
        if(!active)return stack(heading("Haven & Hearth - No Active Subscription",STATUS_GOLD),
            body(width,block("Subscribe to unlock:","• Extra inventory row","• +30% character development","• +100% convenience")),
            body("Haven & Hearth remains free to play. Subscriptions help support the game's continued development.",width),
            invite("Click to subscribe in the official game store.",width));
        String until=expiration(serverTip);
        return stack(heading("Haven & Hearth - Subscription Active",STATUS_GREEN),
            until==null?null:body(width,hearthborn.L10n.format("Active until: %s",until)),
            body(width,block("Benefits:","• Extra inventory row","• +30% character development","• +100% convenience")),
            body("Thank you for supporting Haven & Hearth!",width),
            invite("Click to manage your subscription in the official game store.",width));
    }
    private static java.awt.image.BufferedImage supportTip(int width){
        String title=hearthborn.L10n.tr("Support Hearthborn Client");
        if(Text.sans.canDisplay('\u2764'))title=title+" \u2764";
        return stack(headingText(title,STATUS_GOLD),
            body("Enjoying Hearthborn?",width),
            body("Support the continued development of the client, including new features, bug fixes, and improvements.",width),
            body("Hearthborn is free for everyone. Donations are entirely voluntary and do not unlock exclusive features or provide in-game advantages.",width),
            invite("Click to support Hearthborn on Buy Me a Coffee.",width));
    }
    private static java.awt.image.BufferedImage heading(String key,java.awt.Color ink){return headingText(hearthborn.L10n.tr(key),ink);}
    private static java.awt.image.BufferedImage headingText(String text,java.awt.Color ink){return VERIFIED_TITLE.render(text,ink).img;}
    private static java.awt.image.BufferedImage body(String key,int width){return body(width,hearthborn.L10n.tr(key));}
    private static java.awt.image.BufferedImage body(int width,String text){return BODY.renderwrap(text,width).img;}
    private static String block(String... keys){
        StringBuilder text=new StringBuilder();
        for(String key:keys){
            if(text.length()>0)text.append('\n');
            if(key.startsWith("• "))text.append("• ").append(hearthborn.L10n.tr(key.substring(2)));
            else text.append(hearthborn.L10n.tr(key));
        }
        return text.toString();
    }
    private static java.awt.image.BufferedImage invite(String key,int width){return INVITE.renderwrap(hearthborn.L10n.tr(key),STATUS_GOLD,width).img;}
    private static java.awt.image.BufferedImage stack(java.awt.image.BufferedImage... parts){
        int gap=UI.scale(6),width=0,height=0,shown=0;
        for(java.awt.image.BufferedImage part:parts){if(part==null)continue;width=Math.max(width,part.getWidth());height+=part.getHeight();if(shown++>0)height+=gap;}
        java.awt.image.BufferedImage image=TexI.mkbuf(Coord.of(Math.max(1,width),Math.max(1,height)));
        java.awt.Graphics2D graphics=image.createGraphics();
        int y=0,placed=0;
        for(java.awt.image.BufferedImage part:parts){if(part==null)continue;if(placed++>0)y+=gap;graphics.drawImage(part,0,y,null);y+=part.getHeight();}
        graphics.dispose();
        return image;
    }
    public boolean mousedown(MouseDownEvent event){
        if(event.b!=1)return super.mousedown(event);
        java.net.URI page=donation?DONATE:STORE;
        if(page==null)return true;
        try{ui.wnd.toolkit().browse(page);}catch(java.io.IOException error){ui.error(error.getMessage());}
        return true;
    }
    public void draw(GOut g){
        super.draw(g);
        if(!active){g.chcolor(190,60,60,230);int d=UI.scale(10);Coord at=Coord.of(sz.x-d-UI.scale(5),sz.y-d-UI.scale(5));
            g.line(at,at.add(d,d),UI.scale(2));g.line(at.add(d,0),at.add(0,d),UI.scale(2));g.chcolor();}
    }
    public void dispose(){if(tip!=null)tip.dispose();super.dispose();}
    public void tick(double dt){
        super.tick(dt);
        if(ui==null||parent==null||ui.root.findchild(Charlist.class)==null)return;
        Charlist characters=ui.root.findchild(Charlist.class);
        Avaview avatar=characters.avalink;
        if(avatar==null||!avatar.tvisible())return;
        java.util.List<AccountStatusFlag> flags=new java.util.ArrayList<>();
        for(AccountStatusFlag flag:ui.root.children(AccountStatusFlag.class))if(flag.tvisible())flags.add(flag);
        flags.sort(java.util.Comparator.comparingInt(flag->flag.donation?2:flag.subscribed?1:0));
        int index=flags.indexOf(this);
        if(index<0)return;
        java.util.List<Coord> sizes=new java.util.ArrayList<>();
        for(AccountStatusFlag flag:flags)sizes.add(flag.sz);
        Widget frame=avatar.parent instanceof ProxyFrame?avatar.parent:avatar;
        Coord at=frame.rootpos();
        Coord position=position(ui.root.sz,Coord.of(at.x,characters.firstCharacterTop()),frame.sz,sizes,index);
        move(position.sub(parent.rootpos()));
    }
    /** Keep the badges right of the preview at the first character row's height. */
    static Coord position(Coord screen,Coord avatarAt,Coord avatarSize,Coord badge,int index,int count){
        java.util.List<Coord> sizes=new java.util.ArrayList<>();
        for(int i=0;i<count;i++)sizes.add(badge);
        return position(screen,avatarAt,avatarSize,sizes,index);
    }
    static Coord position(Coord screen,Coord avatarAt,Coord avatarSize,java.util.List<Coord> sizes,int index){
        int margin=UI.scale(12),gap=UI.scale(12);
        int width=0,tallest=0;
        for(int i=0;i<sizes.size();i++){width+=sizes.get(i).x;if(i>0)width+=gap;tallest=Math.max(tallest,sizes.get(i).y);}
        Area artwork=LoginArt.characterBounds(screen);
        int left=Utils.clip(avatarAt.x+avatarSize.x+gap,artwork.ul.x+margin,
            Math.max(artwork.ul.x+margin,artwork.br.x-width-margin));
        int x=left;
        for(int i=0;i<index;i++)x+=sizes.get(i).x+gap;
        int top=Utils.clip(avatarAt.y,artwork.ul.y+margin,
            Math.max(artwork.ul.y+margin,artwork.br.y-tallest-margin));
        return Coord.of(x,top+(tallest-sizes.get(index).y)/2);
    }
    /** Keep the summary off the medals, in the open space under the row. */
    public Coord tooltipPosition(Coord pointer,Coord tip,Coord screen){
        Coord at=rootpos();
        int left=at.x,top=at.y,right=at.x+sz.x,bottom=at.y+sz.y;
        if(ui!=null&&ui.root!=null)for(AccountStatusFlag flag:ui.root.children(AccountStatusFlag.class)){
            if(!flag.tvisible())continue;
            Coord place=flag.rootpos();
            left=Math.min(left,place.x);top=Math.min(top,place.y);
            right=Math.max(right,place.x+flag.sz.x);bottom=Math.max(bottom,place.y+flag.sz.y);
        }
        return tooltipPosition(at,sz,Coord.of(left,top),Coord.of(right-left,bottom-top),tip,screen);
    }
    static Coord tooltipPosition(Coord badgeAt,Coord badgeSize,Coord rowAt,Coord rowSize,Coord tip,Coord screen){
        Coord edge=UI.scale(16,16);
        int gap=UI.scale(10);
        int below=rowAt.y+rowSize.y+gap;
        int above=rowAt.y-gap-tip.y;
        int y=below+tip.y<=screen.y-edge.y?below:above;
        int x=badgeAt.x+badgeSize.x/2-tip.x/2;
        return Coord.of(Utils.clip(x,edge.x,Math.max(edge.x,screen.x-edge.x-tip.x)),
            Utils.clip(y,edge.y,Math.max(edge.y,screen.y-edge.y-tip.y)));
    }
}
