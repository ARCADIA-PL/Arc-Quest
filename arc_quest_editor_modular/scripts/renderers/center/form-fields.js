import {esc} from '../../core/utils.js';

export function field(label, bind, value, type = 'text') {
    return `<div class="f"><label>${label}</label><input data-b="${bind}" type="${type}" value="${esc(value)}" placeholder="${label}"></div>`;
}

export function area(label, bind, value) {
    return `<div class="f"><label>${label}</label><textarea data-b="${bind}" placeholder="${label}">${esc(value)}</textarea></div>`;
}
