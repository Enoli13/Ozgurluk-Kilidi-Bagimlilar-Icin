## Özgürlük Kilidi: Bağımlılar İçin v1.5

### Acil düzeltme: kilit ekranı açılamıyordu
- v1.4'te yasak bir uygulama açılınca önce ana ekrana dönülüyordu. Kilit sürerken ana ekran da yasak sayıldığı için bu sonsuz bir döngüye giriyordu ve kilit ekranı (uygulamanın kendisi) "açılıyor gibi yapıp" hiç açılmıyordu. Bu adım kaldırıldı.
- v1.4'teki asıl düzeltme (kullanım kayıtlarının arka planda hızlı okunması ve günlük sınırın anında devreye girmesi) korunuyor.

### Kurulum
Kilit sürerken tarayıcı açılamayacağı için en kolay yol bilgisayardan kurmak:
```
adb install -r OzgurlukKilidi-BagimlilarIcin-v1.5.apk
```
Uygulama silinmeden güncellenir; ayarların ve süren mod korunur.
