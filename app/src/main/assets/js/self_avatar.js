(function() {
    if (/^\/(i\/flow\/login|login)/.test(location.pathname)) return 'fail';
    var url = '';
    try {
        var s = window.__INITIAL_STATE__;
        var u = s.entities.users.entities[s.session.user_id];
        if (u && u.profile_image_url_https) url = u.profile_image_url_https;
    } catch (e) {}
    if (!url) {
        var img = document.querySelector('[data-testid="DashButton_ProfileIcon_Link"] img, [data-testid="SideNav_AccountSwitcher_Button"] img');
        if (img) url = img.src;
    }
    return url ? JSON.stringify({ avatar: url }) : 'wait';
})()
