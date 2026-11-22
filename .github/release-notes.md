## Özgürlük Kilidi: Bağımlılar İçin v1.13

### Düzeltme: 2 saat ya da mola bitince kilitlemiyordu
- Asıl sebep: HyperOS, uygulama son uygulamalardan kaydırılınca süreci öldürüyor ve Erişilebilirlik servisinin yeniden başlamasına izin vermiyordu. Servis ayarlarda "açık" görünüyor ama hiçbir şey kilitlenmiyordu; uygulama elle açılınca tekrar çalışıyordu.
- Kilit denetleyicisi artık tek bir hatada durmuyor (eskiden durabiliyordu).

### Yeni: yedek bekçi
- Koruma açıkken kalıcı bir bildirimle arka planda çalışan ikinci bir bekçi var. Erişilebilirlik servisi öldürülse bile öndeki uygulamayı kullanım kayıtlarından bulup kilit ekranını açar.
- Öldürülürse kendini birkaç saniye içinde yeniden başlatır; telefon açılınca ve güncellemeden sonra da kendiliğinden başlar.

### Son uygulamalardan kaydırılamaz
- Koruma sürerken uygulama son uygulamalar listesinde görünmez, yani oradan kaydırılıp kapatılamaz.

### Arka planda çalışma izinleri
- 1. kartta yeni düğme: Otomatik başlatma ve Pil "Kısıtlama yok" ekranlarını açar. Bu ekranlar düğmeye bastıktan sonra 2 dakika boyunca koruma sürerken de açılabilir (durdurma, kaldırma, erişilebilirlik ve cihaz yöneticisi ekranları yine kapalı).
- Ana ekranda servis gerçekten çalışmıyorsa "Kilit servisi durdurulmuş!" uyarısı çıkar.

### Kurulum
```
adb install -r OzgurlukKilidi-BagimlilarIcin-v1.13.apk
```
Uygulama silinmeden güncellenir; ayarların ve süren mod korunur. Kurduktan sonra uygulamayı bir kez aç, bildirim iznini ver ve "Arka planda çalışma izinleri"nden ikisini de aç.
