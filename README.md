# 🔒 Özgürlük Kilidi: Bağımlılar İçin — telefon bağımlılığına karşı günlük 2 saat sınırı

> Telefon bağımlılığı için ücretsiz Android uygulama kilidi: serbest bıraktığın uygulamalar dışında günde 2 saati geçince telefon mola saatleri dışında kilitlenir. Seçtiğin tarihe kadar kapatılamaz ve silinemez.

Bu proje [Özgürlük Kilidi (Freedom Lock)](https://github.com/Enoli13/Ozgurluk-Kilidi-Freedom-Lock) uygulamasının fork'udur. Zamanlayıcı, serbest uygulamalar ve takvim özellikleri aynen korunur; üstüne **Bağımlılar İçin** modu eklenmiştir.

⬇ [Son sürümü indir](https://github.com/Enoli13/Ozgurluk-Kilidi-Bagimlilar-Icin/releases/latest)

[🇹🇷 Türkçe](#-türkçe) · [🇬🇧 English](#-english)

---

# 🇹🇷 Türkçe

Özgürlük Kilidi, dijital detoks ve odaklanma için geliştirilmiş bir Android uygulamasıdır. Belirlediğin saat aralığında telefonda yalnızca önceden seçtiğin **serbest uygulamalar** kullanılabilir. Başka bir uygulamayı açmaya çalışırsan uygulama seni kilit ekranına geri gönderir.

## ✨ Özellikler

- **Serbest uygulamalar:** Telefondaki uygulamalar otomatik taranır, kilit sırasında açık kalacakları listeden seçersin; kaç tane olacağına sen karar verirsin. Sosyal medya uygulamaları (WhatsApp hariç) ve Ayarlar serbest listeye eklenemez. Kilit ekranında bu uygulamalar bir ikon ızgarası (grid) olarak görünür ve tüm özellikleriyle kullanılabilir (klavye, dosya/fotoğraf seçici, izin pencereleri dahil).
- **Akıllı zamanlayıcı:** Başlangıç ve bitiş saatini, ayrıca haftanın hangi günlerinde çalışacağını seçersin. Saat aralığı ve en az bir gün seçmek zorunludur. Gece yarısını aşan aralıklar (örneğin 22:00 – 02:00) desteklenir.
- **Geri sayım:** Kilit sırasında bitişe kalan süre ve ilerleme çubuğu görünür. Süre dolunca kilit ekranı kapanır ve engelleme kendiliğinden durur.
- **Etkinlik paneli:** Kilit ekranında bugünkü 📖 kitap, 📱 telefon, 🎓 ders, 🎸 gitar ve 🏋️ spor sürelerin grafik olarak görünür. Telefon süresi sistemden otomatik gelir; diğerlerini dokunarak düzenler, **+ Ekle** ile yenilerini eklersin. Aynı grafik ana ekranda **widget** olarak da kullanılabilir.
- **Takvim (isteğe bağlı):** "Şu güne kadar" seçeneğinde takvimden bir tarih seçersin ve o gün bitene kadar zamanlayıcı hiçbir şekilde kapatılamaz. "Şu günlerde" seçeneğinde tarihleri tek tek eklersin ve o günlerde zamanlayıcı kapatılamaz. Bu günlerde uygulama kaldırma ekranı ve kilidi kapatmaya yarayan ayar sayfaları da açılamaz. Takvim seçilmezse zamanlayıcı bir onayla durdurulabilir.
- **Anında devreye girer:** Kilit saati başladığında yasaklı bir uygulama zaten açıksa birkaç saniye içinde kilit ekranına geçilir.
- **Durum takibi:** İzinlerin verilip verilmediği ve Erişilebilirlik servisi kapanırsa kilidin çalışmadığı ana ekranda gösterilir.
- **Kalıcı ayarlar:** Saatler, günler ve seçilen uygulamalar telefon yeniden başlasa bile kaybolmaz.
- **Ayarlar kilitlenir:** Zamanlayıcı kuruluyken saat, gün ve uygulama seçimi değiştirilemez.
- **Gelen aramalar engellenmez:** Arama ekranı ve çalan alarm kilit yüzünden engellenmez.

## 🆘 Bağımlılar İçin modu

Ana ekrandaki **5 · Bağımlılar İçin** kartından kurulur:

1. **Bitiş tarihi** seç (o gün dahil). Mod bu tarihe kadar çalışır.
2. **Mola saat aralığını** seç (örneğin 20:00 – 21:00). Bu saatlerde her uygulama serbesttir ve geçen süre sayılmaz.
3. **BAĞIMLILAR İÇİN MODUNU BAŞLAT** düğmesine bas, özeti onayla ve açılan ekranda **silinme korumasını (cihaz yöneticisi)** etkinleştir.

Nasıl çalışır:

- **Serbest uygulamalar** (2. karttaki liste) hiç sayılmaz. Ana ekran, klavye, arama ve alarm ekranları da sayılmaz.
- Geri kalan tüm uygulamalarda geçen süre, **ekran açık ve telefon kilidi açıkken** toplanır.
- Günlük toplam **2 saati** geçince telefon **mola saatleri dışında** kilitlenir: yalnızca serbest uygulamalar açılabilir.
- Kilit, **mola saati başlayınca** ya da **gece yarısı** sayaç sıfırlanınca kalkar. Sınıra 10 dakika kala uyarı gösterilir.
- Tarih dolana kadar mod **kapatılamaz**, mola saatleri ve serbest liste **değiştirilemez**, uygulama **silinemez**.
- **Ayarlar açılabilir.** Yalnızca kilidi aşmaya yarayan ekranlar engellenir: bu uygulamanın uygulama bilgisi, Erişilebilirlik ve izin sayfaları, cihaz yöneticisi, tarih-saat, fabrika ayarlarına sıfırlama ve USB hata ayıklama. Bu ekranlardan birine girersen bir önceki sayfaya geri dönülür. Uygulama kaldırma ekranı hiç açılmaz.
- Tarih dolunca mod kendiliğinden kapanır ve cihaz yöneticiliği bırakılır; uygulama normal şekilde silinebilir.

### En sıkı koruma (isteğe bağlı, ileri düzey)

Normal kurulumda telefonu **güvenli modda** başlatmak üçüncü taraf uygulamaları geçici olarak kapatır. Bunu da engellemek istersen uygulamayı bilgisayardan **cihaz sahibi** yapabilirsin. Bu yalnızca telefonda hiç hesap (Google hesabı dahil) yokken çalışır; pratikte sıfırlanmış bir telefonda kurulumdan hemen sonra yapılır:

```bash
adb shell dpm set-device-owner com.ozgurluk.kilidi.bagimli/com.ozgurluk.kilidi.AddictAdminReceiver
```

Cihaz sahibi olunca mod sürerken kaldırma sistem düzeyinde engellenir ve güvenli mod kapatılır. Tarih dolunca bu kısıtlamalar ve cihaz sahipliği otomatik kalkar.

## 📊 Etkinlik paneli ve widget

Kilit ekranında özlü söz yerine **bugünkü etkinlik grafiği** gösterilir:

| Çubuk | Nereden gelir? |
|---|---|
| 📱 Telefon | Sistemden otomatik (elle değiştirilemez) |
| 📖 Kitap, 🎓 Ders, 🎸 Elektro gitar, 🏋️ Spor | Sen girersin |
| Eklediklerin | Sen girersin |

- Bir çubuğa dokununca süresini (saat + dakika), adını ve emojisini düzenleyebilir ya da silebilirsin.
- **+ Ekle** ile istediğin kadar yeni etkinlik eklersin (ad, emoji, renk).
- Süreler her gece yarısı sıfırlanır.
- Telefon süresinin Android'in kendi kayıtlarından (Dijital Denge ile aynı kaynak) gelmesi için **1 · İzinler** kartındaki **Kullanım erişimi** iznini ver. İzin verilmezse uygulama ekranın açık kaldığı süreyi kendisi ölçer.

**Widget:** Ana ekranda boş bir yere uzun bas → **Widget'lar** → **Özgürlük Kilidi: Bağımlılar İçin** → **Etkinlik grafiği**. Widget'ta bir çubuğa dokununca düzenleme penceresi açılır, sağ üstteki **+** ile yeni etkinlik eklenir. Telefon çubuğu widget'tan da değiştirilemez.

## ⚙️ Nasıl çalışır?

Uygulama bir **Erişilebilirlik Servisi** kullanır. Servis, ekrana hangi uygulamanın penceresinin geldiğini (yalnızca paket adını) izler. Kilit saatlerinde seçilmemiş bir uygulama açılırsa Özgürlük Kilidi ekranını öne getirir.

> **Not:** Uygulama Geri, Ana Ekran ve Son Uygulamalar tuşlarını devre dışı bırakmaz. Bu tuşlarla başka bir uygulamaya geçersen kilit ekranına geri döndürülürsün.

## 🔒 Gizlilik

- Servis açılan uygulamanın **paket adını** görür. Koruma sürerken ayrıca yalnızca **Ayarlar** uygulamasındaki ekran yazılarına bakar (tehlikeli ayar sayfalarını tanımak için). Başka uygulamaların içeriğini, yazdıklarını veya kişisel verileri okumaz.
- Telefon süresi için Android'in kullanım kayıtları yalnızca telefonda okunur.
- Uygulama **internet izni istemez**. Hiçbir veri telefondan dışarı çıkmaz.
- Tüm ayarlar yalnızca telefonda saklanır.

## 📋 Gereksinimler

- Android 10 (API 29) veya üzeri
- Derlemek için: [Android Studio](https://developer.android.com/studio)

## 🚀 Kurulum

### 1. Derle

Projeyi klonla ve Android Studio ile aç:

```bash
git clone https://github.com/Enoli13/Ozgurluk-Kilidi-Bagimlilar-Icin.git
```

Ardından **Build → Build Bundle(s) / APK(s) → Build APK(s)** ile `app-debug.apk` dosyasını oluştur ya da terminalde şunu çalıştır:

```bash
./gradlew assembleDebug
```

APK `app/build/outputs/apk/debug/` klasöründe oluşur. Dosyayı telefonuna aktarıp kur.

### 2. Takvim (isteğe bağlı)

Zamanlayıcıyı kapatılamaz yapmak için **4 · Takvim** kartından seçim yap:

- **Yok:** Zamanlayıcı bir onayla durdurulabilir.
- **Şu güne kadar:** Seçtiğin tarih (o gün dahil) bitene kadar zamanlayıcı kapatılamaz, ayarlar değiştirilemez.
- **Şu günlerde:** Eklediğin her tarihte zamanlayıcı kapatılamaz.

Kapatılamaz günlerde kilit saatleri dışında da uygulama kaldırma ekranı ve kilidi kapatmaya yarayan ayar sayfaları açılmaz; böylece Erişilebilirlik servisi kapatılarak ya da uygulama silinerek kilit aşılamaz.

### 3. İzinleri ver

Uygulamayı açınca iki izin gerekir:

1. **Üstte Gösterim İzni:** Kilit ekranını diğer uygulamaların önüne getirmek için.
2. **Erişilebilirlik İzni:** Hangi uygulamanın açıldığını fark etmek için. Ayarlarda "Özgürlük Kilidi: Bağımlılar İçin" servisini aç.

> **Android 13+ notu:** APK'yı Play Store dışından kurduysan Erişilebilirlik anahtarı gri görünebilir. Bu durumda **Ayarlar → Uygulamalar → Özgürlük Kilidi → sağ üstteki ⋮ menüsü → Kısıtlı ayarlara izin ver** seçeneğini kullan, sonra izni tekrar dene.
>
> Google Play Protect, Erişilebilirlik ve üstte gösterim izni isteyen uygulamalar için kurulum sırasında uyarı gösterebilir.

### 4. Kullan

1. **Uygulamaları seç** düğmesiyle serbest kalacak uygulamaları seç ve kaydet.
2. Başlangıç ve bitiş saatini ve en az bir günü seç (zorunlu). İstersen takvimden kapatılamaz günleri belirle.
3. **ZAMANLAYICIYI AKTİF ET** düğmesine bas ve özeti onayla.

Zamanlayıcı kuruluyken uygulamayı açarsan bir sonraki kilide kalan süreyi görürsün. Kilit saati gelince seçilmemiş bir uygulamayı açtığın anda kilit ekranı devreye girer.

## 🛠️ Sorun giderme

**Bir uygulama içinde kilit ekranına atılıyorum (örneğin başka bir cihazda farklı bir dosya seçici):**
Bazı üreticiler kendi sistem pencerelerini kullanır. Android Studio'da Logcat'i `OzgurlukKilidi` etiketiyle filtrele. Kilit ekranına atıldığın anda şuna benzer bir satır çıkar:

```
Engellendi: paket.adi (sinif.adi)
```

Buradaki paket adını `AppBlockerService.kt` dosyasındaki `ALWAYS_ALLOWED` listesine ekleyip yeniden derle.

## ⚠️ Sınırlar

Bu uygulama bir **öz disiplin aracıdır**. "Bağımlılar İçin" modunda normal kurulumla kaldırma ekranları, tehlikeli ayar sayfaları ve cihaz yöneticisinin kapatılması engellenir. Ayar sayfaları ekran yazılarına göre tanındığı için bazı üreticilerin farklı adlandırdığı bir sayfa gözden kaçabilir; yine de telefonu güvenli modda başlatmak (cihaz sahibi kurulumunda kapalıdır), fabrika ayarlarına sıfırlamak ya da bilgisayardan adb kullanmak kilidi aşabilir. Acil aramalar hiçbir zaman engellenmez.

## 📁 Proje yapısı

| Dosya | Görevi |
|---|---|
| `MainActivity.kt` | Ayar ve kilit ekranı, uygulama ızgarası, geri sayım, takvim |
| `AppBlockerService.kt` | Erişilebilirlik servisi; izinsiz uygulamayı fark edip kilit ekranını öne getirir |
| `LockSettings.kt` | Kalıcı ayarlar, takvim, zaman penceresi ve günlük kullanım sayacı |
| `AddictGuard.kt` | Cihaz yöneticisi: Bağımlılar İçin modu sürerken silinmeyi engeller |
| `ActivityStore.kt` | Etkinlik paneli: kategoriler, günlük süreler, sistemden telefon süresi |
| `ChartRenderer.kt` | Grafik çizimi (kilit ekranı ve widget ortak) |
| `ActivityDialogs.kt` | Düzenleme ve yeni etkinlik ekleme pencereleri |
| `ActivityWidget.kt` | Ana ekran widget'ı |

---

# 🇬🇧 English

Freedom Lock is an Android app for digital detox and focus. During the time window you set, only the **allowed apps** you pre-selected can be used. If you try to open anything else, the app sends you back to the lock screen.

## ✨ Features

- **Allowed apps:** The app scans your installed apps and lets you pick which ones stay available during the lock; you decide how many. Social media apps (except WhatsApp) and Settings can't be allowed. They appear as an icon grid on the lock screen and work with all their features (keyboard, file/photo pickers and permission dialogs included).
- **Smart scheduler:** Choose a start time, an end time, and the days of the week it should run. A time range and at least one day are required. Windows that cross midnight (e.g. 22:00 – 02:00) are supported.
- **Countdown:** The remaining time and a progress bar are shown during the lock. When it hits zero, the lock screen closes and blocking stops automatically.
- **Activity panel:** The lock screen shows today's reading, phone, study, guitar and sport time as a bar chart. Phone time comes from the system automatically; tap the others to edit them and use **+ Ekle** to add new ones. The same chart is available as a **home screen widget**.
- **Calendar (optional):** With "Şu güne kadar" (until a date) you pick a date and the timer can't be turned off until that day ends. With "Şu günlerde" (on these days) you add dates one by one and the timer can't be turned off on those days. The uninstall screen and settings pages that could defeat the lock are blocked on those days too. Without a calendar, the timer can be stopped after a confirmation.
- **Kicks in immediately:** If a blocked app is already open when the lock starts, you're moved to the lock screen within a few seconds.
- **Status at a glance:** The home screen shows which permissions are granted and warns you if the Accessibility service was turned off.
- **Persistent settings:** Times, days and selected apps survive a phone restart.
- **Settings locked while armed:** You can't change times, days or apps while the timer is set.
- **Calls are never blocked:** The call screen and ringing alarms are not blocked by the lock.

## 🆘 "For Addicts" mode (Bağımlılar İçin)

Set it up from the **5 · Bağımlılar İçin** card:

1. Pick an **end date** (inclusive). The mode runs until then.
2. Pick a **break window** (e.g. 20:00 – 21:00). Everything is allowed during the break and that time isn't counted.
3. Press **BAĞIMLILAR İÇİN MODUNU BAŞLAT**, confirm, and enable **uninstall protection (device admin)**.

- **Allowed apps** are never counted. Home screen, keyboard, call and alarm screens aren't counted either.
- Time spent in every other app is summed while the screen is on and unlocked.
- Once the daily total passes **2 hours**, the phone locks **outside the break window**: only allowed apps can be opened.
- The lock lifts when the break starts or at midnight, when the counter resets. You get a warning 10 minutes before the limit.
- Until the end date the mode **can't be turned off**, the break window and allowed apps **can't be changed**, and the app **can't be uninstalled**. Settings stays usable; only screens that could defeat the lock (this app's info/accessibility/permission pages, device admin, date & time, factory reset, USB debugging) are blocked, and the uninstall screen never opens.
- After the end date the mode turns itself off and releases device admin, so the app can be uninstalled normally.

**Strictest setup (optional):** make the app device owner on a phone with no accounts:
`adb shell dpm set-device-owner com.ozgurluk.kilidi.bagimli/com.ozgurluk.kilidi.AddictAdminReceiver`.
Uninstalling is then blocked at system level and safe mode is disabled until the end date.

## ⚙️ How it works

The app uses an **Accessibility Service**. It watches which app's window comes to the screen (package name only). If a non-allowed app opens during lock hours, Freedom Lock brings its own screen to the front.

> **Note:** The app does not disable the Back, Home or Recents buttons. If you use them to switch to another app, you are sent back to the lock screen.

## 🔒 Privacy

- The service only sees the **package name** of the app that opened. It does not read screen content, what you type, or personal data.
- The app **does not request internet permission**. No data leaves your phone.
- All settings are stored on the device only.

## 📋 Requirements

- Android 10 (API 29) or newer
- To build: [Android Studio](https://developer.android.com/studio)

## 🚀 Installation

### 1. Build

Clone the project and open it in Android Studio:

```bash
git clone https://github.com/Enoli13/Ozgurluk-Kilidi-Bagimlilar-Icin.git
```

Then use **Build → Build Bundle(s) / APK(s) → Build APK(s)** to create `app-debug.apk`, or run:

```bash
./gradlew assembleDebug
```

The APK is created in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

### 2. Calendar (optional)

Use the **4 · Takvim** card to make the timer impossible to turn off:

- **Yok (none):** The timer can be stopped after a confirmation.
- **Şu güne kadar (until):** Until the chosen date ends (inclusive), the timer can't be stopped and settings can't be changed.
- **Şu günlerde (on these days):** On every date you add, the timer can't be stopped.

On those days, the uninstall screen and lock-defeating settings pages are blocked even outside lock hours, so the lock can't be bypassed by turning off the Accessibility service or uninstalling the app.

### 3. Grant permissions

When you open the app, two permissions are required:

1. **Display over other apps:** so the lock screen can appear on top of other apps.
2. **Accessibility:** to detect which app was opened. Turn on the "Özgürlük Kilidi" service in settings.

> **Android 13+ note:** If you installed the APK from outside the Play Store, the Accessibility switch may be greyed out. In that case go to **Settings → Apps → Özgürlük Kilidi → ⋮ menu (top right) → Allow restricted settings**, then try the permission again.
>
> Google Play Protect may show a warning during installation for apps that request Accessibility and overlay permissions.

### 4. Use it

1. Press **Uygulamaları seç** (Select apps), choose the apps that stay available, then save.
2. Choose the start time, the end time and, if you like, the days.
3. Press **ZAMANLAYICIYI AKTİF ET** (Activate timer) and confirm the summary.

While the timer is armed, opening the app shows the time left until the next lock. Once lock time arrives, the lock screen kicks in the moment you open a non-selected app.

## 🛠️ Troubleshooting

**I get sent back to the lock screen inside an allowed app (e.g. a different file picker on another device):**
Some manufacturers use their own system windows. In Android Studio, filter Logcat by the tag `OzgurlukKilidi`. When you're kicked to the lock screen, a line like this appears:

```
Engellendi: package.name (class.name)
```

Add that package name to the `ALWAYS_ALLOWED` list in `AppBlockerService.kt` and rebuild.

## ⚠️ Limits

Freedom Lock is a **self-discipline tool**, not a security barrier. Without a calendar, uninstalling the app or turning off the Accessibility service bypasses the lock. Even on calendar days, booting into safe mode or changing the phone's date manually can bypass it. The goal isn't to stop you by force, but to create a pause at the moment you reach for a distracting app.

## 📁 Project structure

| File | Purpose |
|---|---|
| `MainActivity.kt` | Settings and lock screen, app grid, countdown, calendar |
| `AppBlockerService.kt` | Accessibility service; detects non-allowed apps and brings the lock screen to the front |
| `LockSettings.kt` | Persistent settings, calendar and time-window calculations |
