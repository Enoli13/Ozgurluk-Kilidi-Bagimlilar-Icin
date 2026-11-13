## Özgürlük Kilidi: Bağımlılar İçin v1.6

### Yeni: serbest listeye bilgisayardan uygulama ekleme
Mod sürerken serbest liste telefondan değiştirilemez. Unuttuğun bir uygulamayı eklemek için artık **USB ile bağlı bilgisayardan** komut gönderebilirsin. Bu komutu telefondaki hiçbir uygulama gönderemez; kilit anlık bir dürtüyle telefondan aşılamaz.

Paket adını bul:
```
adb shell pm list packages | grep -iE "trendyol|hepsiburada|amazon"
```

Ekle (virgülle birden fazla):
```
adb shell am broadcast -a com.ozgurluk.kilidi.bagimli.SERBEST_EKLE -n com.ozgurluk.kilidi.bagimli/com.ozgurluk.kilidi.AdbWhitelistReceiver --es paketler "paket1,paket2"
```

Çıkar: eylemi `com.ozgurluk.kilidi.bagimli.SERBEST_CIKAR` yap.

Sosyal medya uygulamaları bu yolla da serbest yapılamaz.

### Kurulum
```
adb install -r OzgurlukKilidi-BagimlilarIcin-v1.6.apk
```
Uygulama silinmeden güncellenir; ayarların ve süren mod korunur.
