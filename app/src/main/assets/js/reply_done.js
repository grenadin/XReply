(function() {
    if (!/^\/(intent|compose)\/(post|tweet)/.test(location.pathname)) {
        var a = document.querySelector('[data-testid="toast"] a[href*="/status/"]');
        return 'left:' + location.pathname + '|' + (window.__xreplyId || '') + '|' + (a ? a.href : '');
    }
    var toast = document.querySelector('[data-testid="toast"]');
    if (toast && toast.innerText) return 'toast:' + toast.innerText;
    return 'waiting';
})()
