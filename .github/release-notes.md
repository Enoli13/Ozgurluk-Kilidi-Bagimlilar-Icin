## Özgürlük Kilidi: Bağımlılar İçin v1.12

### Düzeltme: Alt tuşlarla kilitten çıkılabiliyordu
- Süre dolduktan sonra geri / son uygulamalar tuşuyla açık bir uygulamaya dönülünce sistem bazen bu geçişi "uygulama ekranı değil" diye bildiriyordu. v1.9'daki ses paneli düzeltmesi bu bildirimleri yok saydığı için uygulama kilitlenmiyordu.
- Artık kilit hem olaylara hem de ekrandaki gerçek uygulama penceresine bakıyor. Bu kontrol her 2 saniyede bir tekrarlanıyor, yani bir olay kaçsa bile en geç birkaç saniye içinde kilit ekranına geri dönülüyor.
- Ses paneli, bildirim çubuğu ve klavye yine kilidi tetiklemiyor.

### Metrolist
- v1.11'deki Metrolist eklemesi aynen duruyor.

### Kurulum
```
adb install -r OzgurlukKilidi-BagimlilarIcin-v1.12.apk
```
Uygulama silinmeden güncellenir; ayarların ve süren mod korunur.
