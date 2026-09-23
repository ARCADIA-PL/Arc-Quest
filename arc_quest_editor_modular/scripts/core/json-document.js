export const MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;
const MAX_DEPTH = 64;
const MAX_NODES = 100000;

// 数据副本与 UI 状态分离，同时在递归规范化之前限制输入规模。
export function cloneJson(value, path = '$') {
    const ancestors = new Set();
    let nodes = 0;
    function copy(input, location, depth) {
        if (++nodes > MAX_NODES) throw new Error(`${location}: JSON 节点数量超过限制`);
        if (depth > MAX_DEPTH) throw new Error(`${location}: JSON 嵌套超过 ${MAX_DEPTH} 层`);
        if (input === null || typeof input === 'string' || typeof input === 'boolean') return input;
        if (typeof input === 'number') {
            if (!Number.isFinite(input) || (Number.isInteger(input) && !Number.isSafeInteger(input))) {
                throw new Error(`${location}: 数值超出编辑器可精确表示的范围`);
            }
            return input;
        }
        if (input === undefined) return undefined;
        if (typeof input !== 'object') throw new Error(`${location}: 必须是 JSON 数据`);
        if (ancestors.has(input)) throw new Error(`${location}: JSON 不允许循环引用`);
        if (!Array.isArray(input) && Object.getPrototypeOf(input) !== Object.prototype
            && Object.getPrototypeOf(input) !== null) throw new Error(`${location}: 必须是普通 JSON 对象`);
        ancestors.add(input);
        try {
            if (Array.isArray(input)) return input.map((item, index) => copy(item, `${location}[${index}]`, depth + 1) ?? null);
            const output = {};
            for (const [key, item] of Object.entries(input)) {
                if (item === undefined) continue;
                // __proto__ 等名称也是 JSON 键，不能通过赋值触发对象原型 setter。
                Object.defineProperty(output, key, {
                    value: copy(item, `${location}.${key}`, depth + 1), enumerable: true, writable: true, configurable: true
                });
            }
            return output;
        } finally {
            ancestors.delete(input);
        }
    }
    return copy(value, path, 0);
}

export function cloneDocument(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('JSON 根节点必须是对象');
    return cloneJson(value);
}

export function parseDocument(text) {
    if (typeof text !== 'string') throw new Error('无法读取 JSON 文本');
    if (text.length > MAX_DOCUMENT_BYTES || new TextEncoder().encode(text).length > MAX_DOCUMENT_BYTES) {
        throw new Error('JSON 文件不能超过 8 MiB');
    }
    return cloneDocument(JSON.parse(text));
}
