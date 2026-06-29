package org.arcadia.arc_quest.websocket;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class ArcQuestWebSocketServer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PORT = 38087;
    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int OP_TEXT = 0x1;
    private static final int OP_CLOSE = 0x8;
    private static final int OP_PING = 0x9;
    private static final int OP_PONG = 0xA;

    private static final Set<Socket> CONNECTIONS = new CopyOnWriteArraySet<>();

    private static volatile ServerSocket serverSocket;
    private static volatile Thread acceptThread;
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
        try {
            cachedFullJson = RegistryCollector.collectAll().toString();
        } catch (Exception e) {
            LOGGER.error("[ArcQuest-WS] Failed to collect registry data", e);
            cachedFullJson = "{}";
        }

        try {
            serverSocket = new ServerSocket(PORT);
            acceptThread = new Thread(ArcQuestWebSocketServer::acceptLoop, "ArcQuest-WS-Accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            LOGGER.info("[ArcQuest-WS] Started on ws://localhost:{}", PORT);
        } catch (IOException e) {
            LOGGER.error("[ArcQuest-WS] Failed to start on port {}", PORT, e);
        }
    }

    public static void stop() {
        cachedFullJson = null;

        for (Socket sock : CONNECTIONS) {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
        CONNECTIONS.clear();

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        LOGGER.info("[ArcQuest-WS] Stopped");
    }

    public static void rebuildAndBroadcast() {
        try {
            cachedFullJson = RegistryCollector.collectAll().toString();
        } catch (Exception e) {
            LOGGER.error("[ArcQuest-WS] Failed to rebuild registry data", e);
            return;
        }
        broadcast(cachedFullJson, true);
    }

    private static void broadcast(String json, boolean isDelta) {
        String type = isDelta ? "delta" : "full";
        String msg = "{\"type\":\"" + type + "\",\"data\":" + json + "}";
        byte[] frame = buildTextFrame(msg);
        for (Socket sock : CONNECTIONS) {
            try {
                OutputStream out = sock.getOutputStream();
                synchronized (sock) {
                    out.write(frame);
                    out.flush();
                }
            } catch (IOException e) {
                CONNECTIONS.remove(sock);
                try { sock.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket sock = serverSocket.accept();
                sock.setSoTimeout(30000);
                Thread t = new Thread(() -> handleConnection(sock), "ArcQuest-WS-Client");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    LOGGER.warn("[ArcQuest-WS] Accept error", e);
                }
            }
        }
    }

    private static void handleConnection(Socket sock) {
        try {
            InputStream in = sock.getInputStream();
            OutputStream out = sock.getOutputStream();

            if (!doHandshake(in, out)) {
                sock.close();
                return;
            }

            CONNECTIONS.add(sock);
            LOGGER.info("[ArcQuest-WS] Editor connected ({} active)", CONNECTIONS.size());

            if (cachedFullJson != null) {
                String msg = "{\"type\":\"full\",\"data\":" + cachedFullJson + "}";
                synchronized (sock) {
                    out.write(buildTextFrame(msg));
                    out.flush();
                }
            }

            readFrames(sock, in, out);
        } catch (IOException e) {
            if (!"Socket closed".equals(e.getMessage())) {
                LOGGER.warn("[ArcQuest-WS] Connection error", e);
            }
        } finally {
            CONNECTIONS.remove(sock);
            try { sock.close(); } catch (IOException ignored) {}
            LOGGER.info("[ArcQuest-WS] Editor disconnected ({} active)", CONNECTIONS.size());
        }
    }

    private static boolean doHandshake(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int total = 0;
        int endIdx;

        while (true) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) return false;
            total += n;
            String head = new String(buf, 0, total, StandardCharsets.UTF_8);
            endIdx = head.indexOf("\r\n\r\n");
            if (endIdx >= 0) break;
            if (total >= buf.length) return false;
        }

        String headers = new String(buf, 0, endIdx, StandardCharsets.UTF_8);
        String key = null;
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                key = line.substring(line.indexOf(':') + 1).trim();
                break;
            }
        }
        if (key == null) return false;

        String accept = computeAccept(key);
        String response =
                "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + accept + "\r\n" +
                "\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
        return true;
    }

    private static void readFrames(Socket sock, InputStream in, OutputStream out) throws IOException {
        byte[] headerBuf = new byte[2];
        int read;

        while ((read = readFully(in, headerBuf, 0, 2)) == 2) {
            boolean fin = (headerBuf[0] & 0x80) != 0;
            int opcode = headerBuf[0] & 0x0F;
            boolean masked = (headerBuf[1] & 0x80) != 0;
            long payloadLen = headerBuf[1] & 0x7F;

            if (payloadLen == 126) {
                byte[] ext = new byte[2];
                if (readFully(in, ext, 0, 2) != 2) break;
                payloadLen = ((ext[0] & 0xFF) << 8) | (ext[1] & 0xFF);
            } else if (payloadLen == 127) {
                byte[] ext = new byte[8];
                if (readFully(in, ext, 0, 8) != 8) break;
                payloadLen = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLen = (payloadLen << 8) | (ext[i] & 0xFF);
                }
            }

            byte[] maskKey = null;
            if (masked) {
                maskKey = new byte[4];
                if (readFully(in, maskKey, 0, 4) != 4) break;
            }

            if (payloadLen > 65536) {
                sendClose(sock, out);
                break;
            }

            byte[] payload = new byte[(int) payloadLen];
            if (payloadLen > 0) {
                if (readFully(in, payload, 0, (int) payloadLen) != (int) payloadLen) break;
            }

            if (masked && maskKey != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskKey[i & 3];
                }
            }

            if (!fin && opcode == OP_TEXT) {
                continue;
            }

            switch (opcode) {
                case OP_TEXT:
                    String text = new String(payload, StandardCharsets.UTF_8);
                    handleTextMessage(sock, out, text);
                    break;
                case OP_PING:
                    synchronized (sock) {
                        out.write(buildControlFrame(OP_PONG, new byte[0]));
                        out.flush();
                    }
                    break;
                case OP_CLOSE:
                    sendClose(sock, out);
                    return;
                default:
                    break;
            }
        }
    }

    private static void handleTextMessage(Socket sock, OutputStream out, String text) {
        if (text.contains("\"ping\"")) {
            try {
                synchronized (sock) {
                    out.write(buildTextFrame("{\"type\":\"heartbeat\"}"));
                    out.flush();
                }
            } catch (IOException e) {
                LOGGER.warn("[ArcQuest-WS] Failed to send heartbeat", e);
            }
            return;
        }

        if (cachedFullJson != null) {
            try {
                String msg = "{\"type\":\"full\",\"data\":" + cachedFullJson + "}";
                synchronized (sock) {
                    out.write(buildTextFrame(msg));
                    out.flush();
                }
            } catch (IOException e) {
                LOGGER.warn("[ArcQuest-WS] Failed to send full data", e);
            }
        }
    }

    private static void sendClose(Socket sock, OutputStream out) {
        try {
            synchronized (sock) {
                out.write(buildControlFrame(OP_CLOSE, new byte[0]));
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    private static String computeAccept(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((key + WS_MAGIC).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    }

    private static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, off + total, len - total);
            if (n < 0) return total == 0 ? -1 : total;
            total += n;
        }
        return total;
    }

    private static byte[] buildTextFrame(String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        return buildFrame(true, OP_TEXT, payload, false);
    }

    private static byte[] buildControlFrame(int opcode, byte[] payload) {
        return buildFrame(true, opcode, payload, false);
    }

    private static byte[] buildFrame(boolean fin, int opcode, byte[] payload, boolean masked) {
        int headerSize = 2;
        if (payload.length > 125 && payload.length <= 0xFFFF) headerSize += 2;
        else if (payload.length > 0xFFFF) headerSize += 8;

        byte[] frame = new byte[headerSize + payload.length];
        frame[0] = (byte) ((fin ? 0x80 : 0x00) | (opcode & 0x0F));

        if (payload.length <= 125) {
            frame[1] = (byte) payload.length;
        } else if (payload.length <= 0xFFFF) {
            frame[1] = (byte) 126;
            frame[2] = (byte) ((payload.length >> 8) & 0xFF);
            frame[3] = (byte) (payload.length & 0xFF);
        } else {
            frame[1] = (byte) 127;
            long len = payload.length;
            for (int i = 7; i >= 0; i--) {
                frame[9 - i] = (byte) ((len >> (8 * i)) & 0xFF);
            }
        }

        System.arraycopy(payload, 0, frame, headerSize, payload.length);
        return frame;
    }
}
