(function(text) {
    var box = document.querySelector('[data-testid="tweetTextarea_0"]');
    if (!box) return 'nobox';
    box.focus();
    return document.execCommand('insertText', false, text) ? 'filled' : 'failed';
})(TEXT)
