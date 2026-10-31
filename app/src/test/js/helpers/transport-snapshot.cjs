'use strict';
// JSON-compatible WebExtension protocol records only, not browser structured clone.
function observeRoutedMessages(routed) {
  if (!Array.isArray(routed)) throw new TypeError('Expected routed messages array');
  const messages = [], push = routed.push;
  routed.push = function (...entries) {
    for (const entry of entries) {
      if (!Array.isArray(entry) || entry.length < 2 || !entry[1] || typeof entry[1] !== 'object')
        throw new TypeError('Expected routed [destination, JSON payload]');
      const encoded = JSON.stringify(entry[1]);
      if (typeof encoded !== 'string') throw new TypeError('Non-JSON message');
      messages.push(JSON.parse(encoded));
    }
    return push.apply(this, entries);
  };
  return { messages, restore() { routed.push = push; } };
}
module.exports = { observeRoutedMessages };
