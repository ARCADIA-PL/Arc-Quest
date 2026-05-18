/**
 * WebSocket 注册表客户端。
 *
 * 状态机: DISCONNECTED → CONNECTING → CONNECTED → DISCONNECTED
 * 降级策略: localStorage 缓存 → 硬编码 fallback
 */
const RegistryClient = {
    _ws: null,
    _state: 'DISCONNECTED',
    _cache: null,
    _listeners: [],
    _pingTimer: null,
    _reconnectTimer: null,

    CACHE_KEY: 'arcquest_registry_cache',
    WS_URL: 'ws://localhost:38087',
    STALE_MS: 24 * 60 * 60 * 1000,
    RECONNECT_MS: 5000,

    // ════════════════════════════════════════
    //  初始化
    // ════════════════════════════════════════

    init() {
        this._loadCache();
        this._connect();
    },

    _loadCache() {
        try {
            const raw = localStorage.getItem(this.CACHE_KEY);
            if (raw) {
                const parsed = JSON.parse(raw);
                if (Date.now() - parsed.timestamp < this.STALE_MS) {
                    this._cache = parsed;
                }
            }
        } catch (e) { /* ignore */ }
    },

    _saveCache(data) {
        try {
            this._cache = data;
            localStorage.setItem(this.CACHE_KEY, JSON.stringify(data));
        } catch (e) { /* quota exceeded */ }
    },

    // ════════════════════════════════════════
    //  连接管理
    // ════════════════════════════════════════

    _connect() {
        if (this._state === 'CONNECTING' || this._state === 'CONNECTED') return;
        this._state = 'CONNECTING';
        this._notify('connecting');

        try {
            this._ws = new WebSocket(this.WS_URL);
            this._ws.onopen = () => {
                this._state = 'CONNECTED';
                this._notify('connected');
                this._startPing();
            };
            this._ws.onmessage = (e) => this._handleMessage(JSON.parse(e.data));
            this._ws.onclose = () => {
                this._state = 'DISCONNECTED';
                this._notify('disconnected');
                this._cleanup();
                this._scheduleReconnect();
            };
            this._ws.onerror = () => {
                if (this._ws) this._ws.close();
            };
        } catch (e) {
            this._state = 'DISCONNECTED';
            this._notify('disconnected');
            this._scheduleReconnect();
        }
    },

    _cleanup() {
        if (this._pingTimer) { clearInterval(this._pingTimer); this._pingTimer = null; }
        this._ws = null;
    },

    _startPing() {
        this._pingTimer = setInterval(() => {
            if (this._ws && this._ws.readyState === WebSocket.OPEN) {
                this._ws.send(JSON.stringify({ type: 'ping' }));
            }
        }, 25000);
    },

    _scheduleReconnect() {
        if (this._reconnectTimer) return;
        this._reconnectTimer = setTimeout(() => {
            this._reconnectTimer = null;
            this._connect();
        }, this.RECONNECT_MS);
    },

    // ════════════════════════════════════════
    //  消息处理
    // ════════════════════════════════════════

    _handleMessage(msg) {
        if (msg.type === 'full' || msg.type === 'delta') {
            if (msg.data && msg.data.registries) {
                this._saveCache(msg.data);
                this._notify('data');
            }
        }
    },

    // ════════════════════════════════════════
    //  对外 API
    // ════════════════════════════════════════

    getRegistry(registryName) {
        if (this._cache && this._cache.registries) {
            return this._cache.registries[registryName] || [];
        }
        return [];
    },

    search(registryName, filter) {
        const all = this.getRegistry(registryName);
        if (!filter) return all.slice(0, 100);
        const q = filter.toLowerCase();
        const result = [];
        for (const e of all) {
            if (e.id.toLowerCase().includes(q) || (e.label && e.label.toLowerCase().includes(q))) {
                result.push(e);
                if (result.length >= 50) break;
            }
        }
        return result;
    },

    count(registryName) {
        return this.getRegistry(registryName).length;
    },

    getState() {
        return this._state;
    },

    isConnected() {
        return this._state === 'CONNECTED';
    },

    onStateChange(fn) {
        this._listeners.push(fn);
    },

    _notify(event) {
        for (const fn of this._listeners) fn(event, this._state);
    }
};

export default RegistryClient;
