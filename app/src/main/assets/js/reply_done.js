(function() {
    var id = window.__xreplyId || '';
    if (id || !/^\/(intent|compose)\/(post|tweet)/.test(location.pathname)) {
        var a = document.querySelector('[data-testid="toast"] a[href*="/status/"]');
        var link = a ? a.href : (id && window.__xreplyUser ? 'https://x.com/' + window.__xreplyUser + '/status/' + id : '');
        return 'left:' + location.pathname + '|' + id + '|' + link;
    }
    var toast = document.querySelector('[data-testid="toast"]');
    if (toast && toast.innerText) return 'toast:' + toast.innerText;
    return 'waiting';
})()
