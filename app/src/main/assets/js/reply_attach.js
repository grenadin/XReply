(function() {
    var inputs = Array.prototype.slice.call(document.querySelectorAll('input[data-testid="fileInput"]'));
    var input = inputs.filter(function(i) { return i.closest('[role="dialog"]'); })[0] || inputs[0];
    if (!input || !window.XReplyMedia) return 'noinput';
    var dt = new DataTransfer();
    for (var i = 0; i < XReplyMedia.count(); i++) {
        var raw = atob(XReplyMedia.data(i));
        var bytes = new Uint8Array(raw.length);
        for (var j = 0; j < raw.length; j++) bytes[j] = raw.charCodeAt(j);
        dt.items.add(new File([bytes], XReplyMedia.name(i), { type: XReplyMedia.type(i) }));
    }
    input.files = dt.files;
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return 'attached:' + dt.files.length;
})()
