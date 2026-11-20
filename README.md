# XReply

[ภาษาไทย](#ภาษาไทย)

XReply lets you reply to X posts when the X app itself can't send replies.

On some phones the X app fails to post replies. We saw this on a Huawei Mate X6 running microG, where the same reply works fine on x.com. XReply sends your reply through X's own web page, so you keep using the X app for reading and only hand the reply over to XReply.

> XReply is an unofficial app. It is not affiliated with, endorsed by, or sponsored by X Corp.

## How to use

1. Open XReply once and tap **Log in to X**. Log in with your X username and password. This login stays inside XReply.
2. In the X app, tap **Share** on a post and pick **XReply**.
3. Write your reply, add up to 4 photos if you want, and tap **Reply**.

XReply posts the reply, opens the post in the X app again and shows a **Reply sent** card. **View post** opens your new reply.

## Features

- Shows the post you are replying to (name, avatar, text, first photo), so you can check that it is the right one
- Up to 4 photos (JPEG, PNG, WebP); large photos are scaled down before upload
- Character counter that follows X's rules (CJK and emoji count as 2, links as 23)
- Two ways to send:
  - **Post automatically** (default): XReply fills in X's reply page in a hidden WebView and presses Post for you
  - **Off**: the reply page opens in Chrome and you press Post yourself
- Optional sending status card, which can be turned off in settings
- English and Thai, light and dark theme

## How it works

XReply opens `https://x.com/intent/post?in_reply_to=<post id>&text=<your text>` in a WebView where you are logged in to X. When the text on the page matches what you wrote, it presses X's Post button. It never posts text you did not write, and it stops and shows the page when something needs you (login, a text mismatch, a photo that did not attach, or a timeout).

The post preview comes from `cdn.syndication.twimg.com` (the data X uses for embedded posts), with `publish.twitter.com/oembed` as a fallback.

## Privacy

- XReply has no server and no analytics. It talks only to X's own websites.
- Your X login is a cookie inside XReply's WebView. **Clear X login** in settings removes it together with the stored web data.
- Photos you attach are copied to the app's cache for the upload and deleted afterwards.

## Risks and limits

- XReply depends on X's web page. If X changes it, auto-posting can stop working until XReply is updated. You can always turn auto-posting off and post from Chrome.
- Pressing Post on your behalf is a form of automation. You send every reply yourself, one at a time, but use XReply at your own risk; your account is your responsibility under X's rules.
- The post preview uses an unofficial endpoint and may stop working; replying still works without it.

## Build

Requirements: Android SDK and JDK 17. The app supports Android 8.0 (API 26) and later.

```sh
./gradlew assembleDebug testDebugUnitTest
```

The APK is in `app/build/outputs/apk/debug/`.

## License

XReply is free software under the [GNU General Public License v3.0](LICENSE).

---

## ภาษาไทย

XReply ช่วยให้ตอบกลับ (Reply) โพสต์ใน X ได้ ในกรณีที่แอป X ส่งตอบกลับไม่ได้

บนมือถือบางรุ่น แอป X ส่งตอบกลับไม่สำเร็จ เราเจอบน Huawei Mate X6 ที่ใช้ microG ขณะที่ตอบกลับผ่านเว็บ x.com ได้ปกติ XReply จึงส่งตอบกลับผ่านหน้าเว็บของ X เอง คุณยังอ่านและใช้งาน X ในแอป X ได้ตามเดิม แค่ส่งการตอบกลับมาให้ XReply

> XReply เป็นแอปไม่เป็นทางการ ไม่เกี่ยวข้อง ไม่ได้รับการรับรอง และไม่ได้รับการสนับสนุนจาก X Corp.

### วิธีใช้

1. เปิด XReply ครั้งแรกแล้วกด **เข้าสู่ระบบ X** ใส่ชื่อผู้ใช้และรหัสผ่าน X การเข้าสู่ระบบนี้อยู่ใน XReply เท่านั้น
2. ในแอป X กด **แชร์** ที่โพสต์ แล้วเลือก **XReply**
3. พิมพ์ข้อความ เพิ่มรูปได้สูงสุด 4 รูป แล้วกด **ตอบกลับ**

XReply จะโพสต์ให้ เปิดโพสต์นั้นในแอป X อีกครั้ง และแสดงการ์ด **ส่งตอบกลับแล้ว** กด **ดูโพสต์** เพื่อเปิดการตอบกลับที่เพิ่งส่ง

### ความสามารถ

- แสดงโพสต์ที่กำลังจะตอบกลับ (ชื่อ รูปโปรไฟล์ ข้อความ รูปแรก) ให้ตรวจได้ว่าตอบถูกคน
- แนบรูปได้สูงสุด 4 รูป (JPEG, PNG, WebP) รูปใหญ่จะถูกย่อก่อนอัปโหลด
- นับจำนวนตัวอักษรตามกติกาของ X (อักษรจีน/ญี่ปุ่น/เกาหลีและอีโมจินับ 2 ลิงก์นับ 23)
- ส่งได้ 2 แบบ:
  - **โพสต์อัตโนมัติ** (ค่าเริ่มต้น): XReply กรอกหน้าตอบกลับของ X ใน WebView ที่ซ่อนอยู่แล้วกด Post ให้
  - **ปิด**: เปิดหน้าตอบกลับใน Chrome แล้วกด Post เอง
- การ์ดแสดงสถานะขณะส่ง ปิดได้ในหน้าตั้งค่า
- ภาษาอังกฤษและภาษาไทย ธีมสว่างและมืด

### ทำงานอย่างไร

XReply เปิด `https://x.com/intent/post?in_reply_to=<รหัสโพสต์>&text=<ข้อความ>` ใน WebView ที่เข้าสู่ระบบ X ไว้ เมื่อข้อความบนหน้าเว็บตรงกับที่คุณพิมพ์ จึงกดปุ่ม Post ของ X จะไม่โพสต์ข้อความที่คุณไม่ได้พิมพ์ และจะหยุดแล้วแสดงหน้าเว็บเมื่อต้องให้คุณจัดการ (ต้องเข้าสู่ระบบ ข้อความไม่ตรง แนบรูปไม่สำเร็จ หรือรอนานเกินไป)

ข้อมูลตัวอย่างโพสต์มาจาก `cdn.syndication.twimg.com` (ข้อมูลที่ X ใช้แสดงโพสต์แบบฝัง) ถ้าใช้ไม่ได้จะใช้ `publish.twitter.com/oembed` แทน

### ความเป็นส่วนตัว

- XReply ไม่มีเซิร์ฟเวอร์และไม่เก็บสถิติการใช้งาน ติดต่อเฉพาะเว็บไซต์ของ X
- การเข้าสู่ระบบ X เก็บเป็นคุกกี้ใน WebView ของ XReply ปุ่ม **ล้างการเข้าสู่ระบบ X** ในหน้าตั้งค่าจะลบคุกกี้พร้อมข้อมูลเว็บที่เก็บไว้
- รูปที่แนบจะถูกคัดลอกไว้ในแคชของแอประหว่างอัปโหลด แล้วลบทิ้งหลังส่ง

### ความเสี่ยงและข้อจำกัด

- XReply อาศัยหน้าเว็บของ X ถ้า X เปลี่ยนหน้าเว็บ การโพสต์อัตโนมัติอาจใช้ไม่ได้จนกว่าจะอัปเดต XReply แต่ยังปิดโพสต์อัตโนมัติแล้วโพสต์ผ่าน Chrome ได้เสมอ
- การกด Post แทนผู้ใช้ถือเป็นการทำงานอัตโนมัติรูปแบบหนึ่ง แม้คุณจะส่งเองทีละข้อความ โปรดใช้ด้วยความระมัดระวัง บัญชีของคุณอยู่ภายใต้กฎของ X และเป็นความรับผิดชอบของคุณเอง
- ตัวอย่างโพสต์ใช้ endpoint ที่ไม่เป็นทางการ อาจใช้ไม่ได้ในอนาคต แต่การตอบกลับยังใช้ได้ตามปกติ

### Build

ต้องมี Android SDK และ JDK 17 แอปรองรับ Android 8.0 (API 26) ขึ้นไป

```sh
./gradlew assembleDebug testDebugUnitTest
```

ไฟล์ APK อยู่ที่ `app/build/outputs/apk/debug/`

### License

XReply เป็นซอฟต์แวร์เสรีภายใต้ [GNU General Public License v3.0](LICENSE)
