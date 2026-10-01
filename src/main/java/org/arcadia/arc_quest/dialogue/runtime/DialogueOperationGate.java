package org.arcadia.arc_quest.dialogue.runtime;

/** 服务端调用栈内的独占操作；释放后允许附属 RETURN 注入继续推进。 */
final class DialogueOperationGate {
    private Scope active;

    Scope enter() {
        if (active != null) return null;
        active = new Scope();
        return active;
    }

    final class Scope implements AutoCloseable {
        private Scope() {
        }

        @Override
        public void close() {
            if (active == this) active = null;
        }
    }
}
