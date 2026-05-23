package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.guide.api.GuideDefinition;

import java.util.List;

final class GuideListPanel {
    private final GuideListScreen screen;
    GuideListPanel(GuideListScreen screen){this.screen=screen;}

    void render(GuiGraphics g,int mouseX,int mouseY,int alpha){int[] r=screen.listRect();HudAnimUtil.drawFrame(g,r[0],r[1],r[2],r[3],withAlpha(0x000000,(int)(alpha*.45f)),withAlpha(screen.getThemeColor(),(int)(alpha*.55f)));List<GuideDefinition> guides=screen.guidesForSelectedCategory();if(guides.isEmpty()){GuideNavigationControls.drawScaledText(screen,g,r[0]+12,r[1]+14,.8f,Component.translatable("gui.arc_quest.guide_list.no_guides").getString(),withAlpha(GuideScreen.TEXT,alpha));GuideNavigationControls.drawScaledText(screen,g,r[0]+12,r[1]+28,.68f,Component.translatable("gui.arc_quest.guide_list.no_guides_hint").getString(),withAlpha(GuideScreen.MUTED,alpha));return;}int visible=Math.max(1,(r[3]-12)/24),maxScroll=Math.max(0,guides.size()-visible),scroll=Math.min(screen.getListScroll(),maxScroll),y=r[1]+6;for(int i=scroll;i<guides.size()&&i<scroll+visible;i++){GuideDefinition guide=guides.get(i);boolean selected=guide.getId().equals(screen.getSelectedGuideId());boolean hovered=hit(mouseX,mouseY,r[0]+4,y,r[2]-8,22);if(selected||hovered)g.fill(r[0]+4,y,r[0]+r[2]-4,y+22,withAlpha(selected?0x203040:0x101820,selected?170:110));if(selected)HudRenderUtil.drawCyberneticEdge(g,r[0]+4,y,22,screen.getThemeColor(),alpha);GuideNavigationControls.drawScaledText(screen,g,r[0]+12,y+6,.8f,guide.getTitle().getString(),withAlpha(GuideScreen.TEXT,alpha));if(!org.arcadia.arc_quest.guide.network.ClientGuideCache.INSTANCE.isSeen(guide.getId()))HudRenderUtil.drawBreathingRhombus(g,r[0]+r[2]-12,y+11,withAlpha(screen.getThemeColor(),alpha),System.currentTimeMillis()/1000f,alpha/255f);y+=24;}if(maxScroll>0){int totalItems=guides.size(),visibleItems=visible,trackH=r[3]-10,th=Math.max(16,(int)(trackH*(visibleItems/(float)totalItems))),travel=Math.max(0,trackH-th),ty=r[1]+5+(int)(travel*(scroll/(double)maxScroll));g.fill(r[0]+r[2]-3,r[1]+5,r[0]+r[2]-1,r[1]+r[3]-5,withAlpha(0x22303A,alpha));g.fill(r[0]+r[2]-3,ty,r[0]+r[2]-1,ty+th,withAlpha(screen.getThemeColor(),(int)(alpha*.7f)));}}
    boolean mouseClicked(double mouseX,double mouseY){int[] r=screen.listRect();List<GuideDefinition> guides=screen.guidesForSelectedCategory();int visible=Math.max(1,(r[3]-12)/24),scroll=Math.min(screen.getListScroll(),Math.max(0,guides.size()-visible)),y=r[1]+6;for(int i=scroll;i<guides.size()&&i<scroll+visible;i++){if(hit(mouseX,mouseY,r[0]+4,y,r[2]-8,22)){screen.selectGuide(guides.get(i).getId());return true;}y+=24;}return false;}
    boolean mouseScrolled(double mouseX,double mouseY,double delta){int[] r=screen.listRect();if(!hit(mouseX,mouseY,r[0],r[1],r[2],r[3]))return false;screen.adjustListScroll(delta<0?1:-1);return true;}
    private boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;} private int withAlpha(int c,int a){return ((a&0xFF)<<24)|(c&0x00FFFFFF);} }
