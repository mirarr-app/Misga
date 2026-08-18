# Misga

**[English](#english)** | **[فارسی](#farsi)**

---

<a id="english"></a>

# Misga - Make Iran's SMS Great Again

An Android SMS application made for Iran's SMS situation.

Iran's SMS has turned into a pile of spam messages and advertisements. Almost every notification is just another spam sound that no one cares about.

Misga is built to fix this. By default it ships with predefined regex filters that silence those messages and mark them as spam.

## Features

- **Regex filters with allowlist priority.** Allowlist rules are evaluated first, so you will never miss an important OTP code or an important message.
- **A builtin filter list, fully yours.** The app already includes presets for Iranian spam, ads, and scams. You can keep them, disable them, or delete everything and build your own list from scratch.
- **Add any filter you want.** Open Filter Studio and add a regex (or plain-text) rule. If you are not comfortable writing regex, ask your AI agent to create a pattern that blocks a specific kind of spam, then paste it into the app.
- **Material 3 Expressive.** The UI follows Google's latest Material Design 3 patterns.

## How filtering works

Every incoming message is checked in this order:

1. **Allowlist** — always delivered with a normal notification. Built-in presets cover login codes, banking OTPs (رمز پویا / رمز دوم), and other verification messages.
2. **Blocklist** — matching messages are marked as spam: no sound, no badge, collapsed in the conversation.
3. **Sender settings** — you can also mute or block a specific sender from the chat.

Because allowlist wins, a promotional message that also contains a real verification code still gets through.

Set Misga as your **default SMS app** so it can intercept messages in real time. Android only delivers incoming SMS to the default messaging app.

## Install

Android 8.0 (API 26) or newer is required.

Download the signed `misga-*-universal.apk` from the [GitHub Releases](https://github.com/mirarr-app/Misga/releases) page.

Install the APK, open Misga, grant SMS permissions, and set it as the default SMS app when prompted.

## Custom filters

Filter Studio is where rules live. You can:

- Add allowlist or blocklist rules (regex or substring)
- Enable, disable, edit, or delete any rule, including the builtins
- Test a pattern against sample text before saving
- Export and import the rule list as JSON

A typical prompt for an AI agent:

> Write a Java regex for Misga that matches Iranian SMS of this kind: \[paste an example\]. It should not match OTP or bank verification messages.

Then add the pattern in Filter Studio as a blocklist rule.

## Build from source

JDK 17 and the Android SDK are required.

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

---

<a id="farsi"></a>

<div dir="rtl" lang="fa">

# میسگا - Make Iran's SMS Great Again

یک برنامهٔ پیامک اندروید که برای وضعیت پیامک در ایران ساخته شده است.

پیامک در ایران به انبوهی از اسپم و تبلیغات تبدیل شده است. تقریباً هر اعلان فقط یک صدای اسپم دیگر است که کسی به آن توجه نمی‌کند.

میسگا برای حل همین مشکل ساخته شده است. برنامه از ابتدا با فیلترهای ازپیش‌تعریف‌شدهٔ regex می‌آید تا این پیام‌ها را بی‌صدا کند و به‌عنوان اسپم علامت بزند.

## ویژگی‌ها

- **فیلتر regex با اولویت لیست سفید.** قوانین لیست سفید اول بررسی می‌شوند؛ بنابراین کد تأیید مهم یا پیام مهم را از دست نمی‌دهید.
- **لیست فیلتر داخلی که مال خودتان است.** برنامه از قبل پیش‌فرض‌هایی برای اسپم، تبلیغات و کلاهبرداری‌های رایج ایران دارد. می‌توانید آن‌ها را نگه دارید، خاموش کنید، یا همه را پاک کنید و لیست خودتان را از صفر بسازید.
- **هر فیلتری که بخواهید اضافه کنید.** Filter Studio را باز کنید و یک قانون regex (یا متن ساده) بسازید. اگر نوشتن regex برایتان راحت نیست، از ایجنت هوش مصنوعی بخواهید الگویی برای مسدود کردن نوع خاصی از اسپم بسازد و آن را در برنامه اضافه کنید.
- **Material 3 Expressive.** رابط کاربری از آخرین الگوهای طراحی Material Design 3 گوگل پیروی می‌کند.

## فیلتر چطور کار می‌کند

هر پیام ورودی به این ترتیب بررسی می‌شود:

1. **لیست سفید** — همیشه با اعلان عادی می‌رسد. پیش‌فرض‌های داخلی کد ورود، رمز پویا / رمز دوم بانکی، و دیگر پیام‌های تأیید را پوشش می‌دهند.
2. **لیست سیاه** — پیام‌های منطبق اسپم می‌شوند: بدون صدا، بدون نشان اعلان، و جمع‌شده داخل گفتگو.
3. **تنظیمات فرستنده** — از داخل گفتگو هم می‌توانید یک فرستنده را بی‌صدا یا مسدود کنید.

چون لیست سفید اولویت دارد، حتی اگر پیام تبلیغاتی همزمان یک کد تأیید واقعی داشته باشد، آن پیام می‌رسد.

میسگا را به‌عنوان **برنامهٔ پیامک پیش‌فرض** تنظیم کنید تا بتواند پیام‌ها را در لحظه رهگیری کند. اندروید پیامک ورودی را فقط به برنامهٔ پیام‌رسان پیش‌فرض می‌دهد.

## نصب

اندروید ۸٫۰ (API ۲۶) یا جدیدتر لازم است.

`misga-*-universal.apk` امضاشده را از صفحهٔ [GitHub Releases](https://github.com/mirarr-app/Misga/releases) دانلود کنید.

APK را نصب کنید، میسگا را باز کنید، دسترسی پیامک را بدهید، و وقتی پرسیده شد آن را برنامهٔ پیامک پیش‌فرض کنید.

## فیلترهای سفارشی

قوانین در Filter Studio نگهداری می‌شوند. می‌توانید:

- قانون لیست سفید یا لیست سیاه اضافه کنید (regex یا زیررشته)
- هر قانونی را، از جمله پیش‌فرض‌ها، روشن، خاموش، ویرایش یا حذف کنید
- قبل از ذخیره، الگو را روی یک متن نمونه آزمایش کنید
- لیست قوانین را به‌صورت JSON خروجی بگیرید یا وارد کنید

نمونهٔ درخواست برای ایجنت هوش مصنوعی:

> یک regex جاوا برای میسگا بنویس که این نوع پیامک ایرانی را بگیرد: \[یک نمونه بچسبانید\]. نباید با OTP یا پیام تأیید بانکی منطبق شود.

بعد الگو را در Filter Studio به‌عنوان قانون لیست سیاه اضافه کنید.

## ساخت از روی سورس

JDK ۱۷ و Android SDK لازم است.

```bash
./gradlew assembleDebug
```

APK دیباگ در مسیر `app/build/outputs/apk/debug/` ساخته می‌شود.

</div>
