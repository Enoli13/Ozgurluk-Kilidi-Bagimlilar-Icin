## Özgürlük Kilidi: Bağımlılar İçin v1.4

### Düzeltme: mola bitince yasak uygulamalar birkaç saniye açılabiliyordu
- Günlük sınır kilidi sürerken uygulama çekmecesinden açılan bir uygulama, kilit ekranı gelmeden önce birkaç saniye kullanılabiliyordu. Sebebi, kilit kararı verilmeden önce günün tüm kullanım kayıtlarının her seferinde baştan okunmasıydı; akşam saatlerinde bu saniyeler sürüyordu.
- Kullanım kayıtları artık **arka planda ve yalnızca yeni gelenler** okunarak hesaplanıyor; kilit kararı beklemiyor.
- Günlük 2 saat bir kez dolduğunda bu o gün için kaydediliyor; mola bitince kilit anında geri geliyor, uygulama yeniden başlasa bile.
- Yasak bir uygulama açıldığında önce ana ekrana dönülüyor, ardından kilit ekranı açılıyor; böylece uygulama önden hemen çekiliyor.

### Kurulum
v1.2 veya v1.3 kuruluysa bu APK'yı doğrudan kur: uygulama **silinmeden güncellenir**, ayarların ve süren mod korunur.
