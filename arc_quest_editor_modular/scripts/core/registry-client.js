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
    _nsCache: {},
    _listeners: [],
    _pingTimer: null,
    _reconnectTimer: null,

    CACHE_KEY: 'arcquest_registry_cache',
    WS_URL: 'ws://localhost:38087',
    STALE_MS: 24 * 60 * 60 * 1000,
    RECONNECT_MS: 5000,

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

    _handleMessage(msg) {
        if (msg.type === 'full' || msg.type === 'delta') {
            if (msg.data && msg.data.registries) {
                this._saveCache(msg.data);
                this._nsCache = {};
                this._notify('data');
            }
        }
    },

    getRegistry(registryName) {
        if (this._cache && this._cache.registries) {
            return this._cache.registries[registryName] || [];
        }
        return [];
    },

    listByNamespace(registryName) {
        if (this._nsCache[registryName]) return this._nsCache[registryName];
        const all = this.getRegistry(registryName);
        const map = new Map();
        for (const e of all) {
            const colon = e.id.indexOf(':');
            const ns = colon >= 0 ? e.id.substring(0, colon) : 'unknown';
            if (!map.has(ns)) map.set(ns, []);
            map.get(ns).push(e);
        }
        for (const [ns, entries] of map) {
            entries.sort((a, b) => (a.label || a.id).localeCompare(b.label || b.id));
        }
        const sorted = new Map(
            [...map.entries()].sort((a, b) => {
                if (a[0] === 'minecraft') return -1;
                if (b[0] === 'minecraft') return 1;
                return b[1].length - a[1].length || a[0].localeCompare(b[0]);
            })
        );
        this._nsCache[registryName] = sorted;
        return sorted;
    },

    search(registryName, filter, namespace) {
        let all = this.getRegistry(registryName);
        if (namespace) {
            all = all.filter(e => {
                const colon = e.id.indexOf(':');
                return colon >= 0 && e.id.substring(0, colon) === namespace;
            });
        }
        if (!filter) return all.slice();
        const q = filter.toLowerCase();
        const result = [];
        for (const e of all) {
            if (e.id.toLowerCase().includes(q)
                    || (e.label && e.label.toLowerCase().includes(q))
                    || this._matchNamespace(e.id, q)) {
                result.push(e);
            }
        }
        result.sort((a, b) => (a.label || a.id).localeCompare(b.label || b.id));
        return result;
    },

    _matchNamespace(id, q) {
        const colon = id.indexOf(':');
        if (colon < 0) return false;
        return id.substring(0, colon).toLowerCase().includes(q);
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
