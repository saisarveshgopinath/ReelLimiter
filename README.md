# ReelLimiter 📱

A clean, minimalist Android app designed with Google's **Material 3 (Material You)** design guidelines that limits Instagram Reels viewing to a daily maximum of **20 minutes**, while leaving your **DMs, regular feed, and Stories completely untouched**.

---

## ⬇️ Direct APK Download

Click below to download the latest `.apk` file directly to your phone:

👉 **[Download ReelLimiter.apk (Latest Release)](https://github.com/saisarveshgopinath/ReelLimiter/releases/latest/download/ReelLimiter.apk)**

Or visit the **[Releases Page](https://github.com/saisarveshgopinath/ReelLimiter/releases)** on the right sidebar of the repo.

---

## ✨ Features
- **Precise Reel Recognition**: Uses Android's `AccessibilityService` to detect when an Instagram Reel is playing or when the Reels tab is selected.
- **DMs & Feed Unaffected**: Only Reels trigger the timer. Chats, feed browsing, and Stories are free from limitations.
- **Daily Quota Cap**: Choose a daily limit up to 20 minutes (5m, 10m, 15m, 20m). Automatically resets at midnight.
- **Smart Non-intrusive Blocking**: Once your limit is reached, entering Reels triggers a back navigation and presents a Material You alert overlay.
- **Dynamic Theming**: Adapts to your Google Pixel wallpaper colors (Monet dynamic color engine on Android 12+).

---

## 🛠️ Setup Instructions on your Pixel

1. Download **`ReelLimiter.apk`** from the link above and tap to install.
2. Open **ReelLimiter**:
   - Tap **Enable** under **Accessibility Service**, locate **ReelLimiter**, and switch it to **On**.
   - Tap **Enable** under **Display Over Other Apps** to allow the alert banner.
3. Select your daily limit (up to 20m) and you're good to go!
