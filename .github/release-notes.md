## Özgürlük Kilidi: Bağımlılar İçin v1.9

### Düzeltme: Ses tuşu kilit ekranına atıyordu
- Günlük 2 saat bittikten sonra ses açma/kısma tuşuna basınca açılan ses paneli (HyperOS'ta ayrı bir sistem paketinden geliyor) yasak uygulama sanılıyordu. Artık ses paneli, kontrol merkezi, açılır menüler ve diyaloglar gibi **uygulama ekranı olmayan pencereler** kilidi tetiklemiyor.

### Düzeltme: WhatsApp'ta fotoğraf gönderirken kilit ekranına atılma
- WhatsApp gibi serbest bir uygulama fotoğraf eklemek için galeriyi, fotoğraf seçiciyi ya da kamerayı açtığında bu ekranlar artık o uygulamanın parçası sayılıyor; kilit ekranına atılmıyorsun ve bu süre 2 saate sayılmıyor.
- Galeri ana ekrandan doğrudan açılırsa kilitli kalmaya devam ediyor; galeriden başka bir uygulamaya geçince izin bitiyor. Tarayıcılar ve sosyal medya uygulamaları bu izni hiçbir şekilde alamaz.

### Kurulum
```
adb install -r OzgurlukKilidi-BagimlilarIcin-v1.9.apk
```
Uygulama silinmeden güncellenir; ayarların ve süren mod korunur.
