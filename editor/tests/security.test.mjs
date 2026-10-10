import assert from 'node:assert/strict';
import {mergeAttributes} from '@tiptap/core';
const input=JSON.parse('{"__proto__":{"onerror":"bad","src":"remote"},"class":"safe"}');
const merged=mergeAttributes(input,{class:'second'});
assert.equal(Object.getPrototypeOf(merged),Object.prototype);
assert.equal(merged.onerror,undefined);assert.equal(merged.src,undefined);
assert.deepEqual(Object.keys(merged),['class']);assert.equal(merged.class,'safe second');
console.log('GHSA-cp6q-959q-f8rh regression: pass');
