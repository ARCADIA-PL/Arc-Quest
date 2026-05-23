package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.guide.api.GuideCategory;

import java.util.List;

final class GuideCategoryTabs {
    private final GuideListScreen screen;
    GuideCategoryTabs(GuideListScreen screen){this.screen=screen;}

    void render(GuiGraphics g,int mouseX,int mouseY,int alpha){int[] r=screen.tabsRect();g.fill(r[0],r[1],r[0]+r[2],r[1]+r[3],GuideScreen.BG);g.fill(r[0],r[1]+r[3]-1,r[0]+r[2],r[1]+r[3],withAlpha(screen.getThemeColor(),alpha));List<GuideCategory> cats=screen.visibleCategories();int x=r[0]+10;for(GuideCategory cat:cats){int w=Math.max(44,Minecraft.getInstance().font.width(cat.getDisplayName())+16);boolean selected=cat.getId().equals(screen.getSelectedCategoryId());boolean hovered=hit(mouseX,mouseY,x,r[1]+3,w,16);if(selected||hovered)g.fill(x,r[1]+3,x+w,r[1]+19,withAlpha(selected?0x203040:0x101820,selected?180:120));if(selected)g.fill(x,r[1]+19,x+w,r[1]+21,withAlpha(screen.getThemeColor(),alpha));GuideNavigationControls.drawScaledText(screen,g,x+6,r[1]+7,.78f,cat.getDisplayName().getString(),withAlpha(selected?GuideScreen.TEXT:GuideScreen.SUB,alpha));x+=w+6;}}
    boolean mouseClicked(double mouseX,double mouseY){int[] r=screen.tabsRect();List<GuideCategory> cats=screen.visibleCategories();int x=r[0]+10;for(GuideCategory cat:cats){int w=Math.max(44,Minecraft.getInstance().font.width(cat.getDisplayName())+16);if(hit(mouseX,mouseY,x,r[1]+3,w,16)){screen.selectCategory(cat.getId());return true;}x+=w+6;}return false;}
    private boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;} private int withAlpha(int c,int a){return ((a&0xFF)<<24)|(c&0x00FFFFFF);} }
