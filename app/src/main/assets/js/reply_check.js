(function(expected, mediaCount) {
    var path = location.pathname;
    if (path.indexOf('/login') >= 0 || path.indexOf('/flow/') >= 0) return 'login';
    if (!/^\/(intent|compose)\/(post|tweet)/.test(path)) return 'other:' + path;
    var norm = function(s) { return (s || '').replace(/\s+/g, ' ').trim(); };
    var boxes = Array.prototype.slice.call(document.querySelectorAll('[data-testid="tweetTextarea_0"]'));
    var btn = document.querySelector('[data-testid="tweetButton"]');
    if (!boxes.length || !btn) return 'waiting:' + (boxes.length ? '' : 'nobox') + (btn ? '' : 'nobtn');
    var texts = boxes.map(function(b) { return norm(b.innerText || b.textContent); });
    if (texts.indexOf(norm(expected)) < 0) {
        return texts.join('') === '' ? 'waiting:empty' : 'mismatch:' + JSON.stringify(texts) + ' vs ' + JSON.stringify(norm(expected));
    }
    if (mediaCount > 0) {
        var scope = document.querySelector('[role="dialog"]') || document;
        var att = scope.querySelector('[data-testid="attachments"]');
        var imgs = att ? att.querySelectorAll('img').length : 0;
        var busy = att ? !!att.querySelector('[role="progressbar"]') : false;
        if (imgs === 0 && !busy) return 'needmedia';
        if (imgs < mediaCount || busy) return 'waiting:upload';
    }
    if (btn.getAttribute('aria-disabled') === 'true' || btn.disabled) return 'waiting:disabled';
    return 'ready';
})(EXPECTED, MEDIA_COUNT)
