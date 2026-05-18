package org.arcadia.arc_quest.quest.network;

import com.mojang.logging.LogUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class ArcQuestWebSocketServer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PORT = 38087;
    private static final Set<ChannelHandlerContext> CONNECTIONS = new CopyOnWriteArraySet<>();

    private static volatile EventLoopGroup bossGroup;
    private static volatile EventLoopGroup workerGroup;
    private static volatile String cachedFullJson;

    private ArcQuestWebSocketServer() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        start();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        stop();
    }

    public static void start() {
        cachedFullJson = RegistryCollector.collectAll().toString();
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new WebSocketChannelInitializer())
                    .bind(PORT)
                    .sync();
            LOGGER.info("[ArcQuest-WS] Started on ws://localhost:{}", PORT);
        } catch (Exception e) {
            LOGGER.error("[ArcQuest-WS] Failed to start on port {}", PORT, e);
        }
    }

    public static void stop() {
        cachedFullJson = null;
        CONNECTIONS.clear();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        LOGGER.info("[ArcQuest-WS] Stopped");
    }

    public static void rebuildAndBroadcast() {
        cachedFullJson = RegistryCollector.collectAll().toString();
        broadcast(cachedFullJson, true);
    }

    private static void broadcast(String json, boolean isDelta) {
        String type = isDelta ? "delta" : "full";
        String msg = "{\"type\":\"" + type + "\",\"data\":" + json + "}";
        for (ChannelHandlerContext ctx : CONNECTIONS) {
            if (ctx.channel().isOpen()) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(msg));
            }
        }
    }

    private static void handleMessage(ChannelHandlerContext ctx, String text) {
        try {
            int typeStart = text.indexOf("\"type\"");
            if (typeStart < 0) return;

            if (text.contains("\"ping\"")) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"type\":\"heartbeat\"}"));
                return;
            }

            if (cachedFullJson != null) {
                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame("{\"type\":\"full\",\"data\":" + cachedFullJson + "}"));
            }
        } catch (Exception e) {
            LOGGER.warn("[ArcQuest-WS] Invalid message", e);
        }
    }

    private static class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {
            ch.pipeline().addLast(
                    new HttpServerCodec(),
                    new HttpObjectAggregator(65536),
                    new WebSocketServerProtocolHandler("/", null, true, 65536),
                    new WebSocketFrameHandler()
            );
        }
    }

    private static class WebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            CONNECTIONS.add(ctx);
            LOGGER.info("[ArcQuest-WS] Editor connected ({} active)", CONNECTIONS.size());
            if (cachedFullJson != null) {
                ctx.channel().writeAndFlush(
                        new TextWebSocketFrame("{\"type\":\"full\",\"data\":" + cachedFullJson + "}"));
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
            handleMessage(ctx, frame.text());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            CONNECTIONS.remove(ctx);
            LOGGER.info("[ArcQuest-WS] Editor disconnected ({} active)", CONNECTIONS.size());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.error("[ArcQuest-WS] Error", cause);
            ctx.close();
        }
    }
}
