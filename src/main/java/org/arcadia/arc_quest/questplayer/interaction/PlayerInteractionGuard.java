package org.arcadia.arc_quest.questplayer.interaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 只在调用栈存续期间持有 UUID。普通交互允许嵌套，恢复必须独占该玩家。 */
public final class PlayerInteractionGuard {
    public static final PlayerInteractionGuard INSTANCE = new PlayerInteractionGuard();
    private final Map<UUID, State> states = new HashMap<>();

    public synchronized Scope enter(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        State state = states.get(playerId);
        if (state != null && state.restoring) return null;
        if (state == null) {
            state = new State(false);
            states.put(playerId, state);
        }
        state.depth = Math.incrementExact(state.depth);
        return new Scope(playerId, state);
    }

    public synchronized Scope restore(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (states.containsKey(playerId)) return null;
        State state = new State(true);
        state.depth = 1;
        states.put(playerId, state);
        return new Scope(playerId, state);
    }

    public synchronized boolean isRestoring(UUID playerId) {
        State state = states.get(playerId);
        return state != null && state.restoring;
    }

    public final class Scope implements AutoCloseable {
        private final UUID playerId;
        private final State state;
        private boolean closed;

        private Scope(UUID playerId, State state) {
            this.playerId = playerId;
            this.state = state;
        }

        @Override
        public void close() {
            synchronized (PlayerInteractionGuard.this) {
                if (closed) return;
                closed = true;
                if (--state.depth == 0) states.remove(playerId, state);
            }
        }
    }

    private static final class State {
        final boolean restoring;
        int depth;

        State(boolean restoring) { this.restoring = restoring; }
    }
}
