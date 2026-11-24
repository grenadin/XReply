# XReply

XReply lets you reply to X posts when the X app itself can't send replies.

On some phones the X app fails to post replies. We saw this on a Huawei Mate X6 running microG, where the same reply works fine on x.com. XReply sends your reply through X's own web page, so you keep using the X app for reading and only hand the reply over to XReply.

> XReply is an unofficial app. It is not affiliated with, endorsed by, or sponsored by X Corp.

## Install

Download `XReply-<version>.apk` from [Releases](https://github.com/grenadin/XReply/releases) and open it on your phone. Android asks you to allow installing apps from your browser or file manager the first time.

You can check the file against the `.sha256` file next to it. The APK is signed with this certificate (SHA-256):

```
B0:61:D7:8D:F9:37:B9:FB:02:EC:3D:B9:97:E3:FC:8E:9B:32:D1:DE:9C:DB:9C:41:8D:AD:DF:63:03:34:89:19
```

If you built XReply yourself before, uninstall that build first: Android does not install an app over one signed with a different key.

## How to use

1. Open XReply once and tap **Log in to X**. Log in with your X username and password. This login stays inside XReply.
2. In the X app, tap **Share** on a post and pick **XReply**.
3. Write your reply, add up to 4 photos, or both, and tap **Reply**. A reply with only photos works too.

XReply posts the reply, opens the post in the X app again and shows a **Reply sent** card. **View post** opens your new reply.

## Features

- Shows the post you are replying to (name, avatar, text, first photo), so you can check that it is the right one
- Up to 4 photos (JPEG, PNG, WebP), with or without text; large photos are scaled down before upload
- Character counter that follows X's rules (CJK and emoji count as 2, links as 23)
- Two ways to send:
  - **Post automatically** (default): XReply fills in X's reply page in a hidden WebView and presses Post for you. In our tests a text reply was sent about 1.5 seconds after tapping **Reply**; photos take longer while X processes them
  - **Off**: the reply page opens in Chrome and you press Post yourself (text only)
- Optional sending status card, which can be turned off in settings
- English and Thai, light and dark theme

## How it works

While you write, XReply already opens X's reply page, `https://x.com/intent/post?in_reply_to=<post id>`, in a hidden WebView where you are logged in to X. Photos, videos and web fonts are skipped while the page is hidden, since nobody sees them. When you tap **Reply**, XReply types your text into the page, attaches your photos, and presses X's Post button once the text on the page matches what you wrote. If there is no prepared page (for example, you logged in only after opening the reply screen), XReply opens the page with your text in the address (`&text=<your text>`) instead. It never posts text you did not write, and it stops and shows the page when something needs you (login, a text mismatch, a photo that did not attach, or a timeout).

The post preview comes from `cdn.syndication.twimg.com` (the data X uses for embedded posts), with `publish.twitter.com/oembed` as a fallback. Posts from protected accounts are not available there, so XReply then opens the post on x.com in a hidden WebView with your login and reads the name, text and photo from the page. Your own avatar next to the reply box is read from x.com the same way and kept for a day.

## Privacy

- XReply has no server and no analytics. It talks only to X's own websites.
- Your X login is a cookie inside XReply's WebView. **Clear X login** in settings removes it together with the stored web data.
- Photos you attach are copied to the app's cache for the upload and deleted afterwards.

## Risks and limits

- XReply depends on X's web page. If X changes it, auto-posting can stop working until XReply is updated. You can always turn auto-posting off and post from Chrome.
- Pressing Post on your behalf is a form of automation. You send every reply yourself, one at a time, but use XReply at your own risk; your account is your responsibility under X's rules.
- The post preview uses unofficial endpoints and X's web page, so it may stop working; replying still works without it.

## Build

Requirements: Android SDK and JDK 17. The app supports Android 8.0 (API 26) and later.

```sh
./gradlew assembleDebug testDebugUnitTest
```

The APK is in `app/build/outputs/apk/debug/`. Release builds are signed only when `xreplySigningFile` points to a properties file with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.

## License

XReply is free software under the [GNU General Public License v3.0](LICENSE).
