(function() {
    if (!window.__xreplyHooked) {
        window.__xreplyHooked = true;
        var grab = function(body) {
            try {
                var d = typeof body === 'string' ? JSON.parse(body) : body;
                var r = d.data.create_tweet.tweet_results.result;
                var id = r.rest_id || (r.tweet && r.tweet.rest_id);
                if (id) window.__xreplyId = id;
            } catch (e) {}
        };
        var origFetch = window.fetch;
        if (origFetch) window.fetch = function(input) {
            var p = origFetch.apply(this, arguments);
            try {
                var u = typeof input === 'string' ? input : (input && input.url) || '';
                if (/CreateTweet/.test(u)) p.then(function(r) { r.clone().text().then(grab); }).catch(function() {});
            } catch (e) {}
            return p;
        };
        var origOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(m, u) {
            if (/CreateTweet/.test(String(u))) this.addEventListener('load', function() {
                try { grab(this.responseType === '' || this.responseType === 'text' ? this.responseText : this.response); } catch (e) {}
            });
            return origOpen.apply(this, arguments);
        };
    }
    var btn = document.querySelector('[data-testid="tweetButton"]');
    if (!btn || btn.getAttribute('aria-disabled') === 'true') return 'no';
    btn.click();
    return 'clicked';
})()
