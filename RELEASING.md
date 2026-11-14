# Ödedim APK dağıtımı

Güncel paket: 1.0.1 (versionCode: 2).

1.0.0 paketinde cihazda açılış çökmesi bildirildi. Bu paket dağıtılmamalı.
1.0.1 paketinde deneysel kısmi R8 optimizasyonu kaldırıldı ve küçültme kapatıldı.
Cihaz Logcat kaydı, AndroidX InitializationProvider / ProcessLifecycleInitializer
başlangıcında küçültülmüş Kotlin koleksiyon sınıfına erişimin IllegalAccessError
ile başarısız olduğunu doğruladı. Küçültme kapalı kalır; yeniden etkinleştirilmeden
önce imzalı release APK gerçek cihazda açılış testinden geçirilmelidir.

Aynı release anahtarı kullanıldığı için 1.0.0 üzerine uygulama verileri
silinmeden güncellenebilir. 1.0.1 APK imzası doğrulandı; boyutu 8.348.529 bayt.
106 unit test ve release lint kontrolü geçti. 1.0.1 paketinin gerçek telefonda
açılıp çalıştığı kullanıcı tarafından doğrulandı.

## APK oluşturma

Kaynak kodunu Android Studio'da açıp debug sürümünü çalıştırmak için release
anahtarı gerekmez. Aşağıdaki görev, yalnızca yerel imzalama dosyaları bulunan
bilgisayarda imzalı release APK oluşturur.

Android Studio'da Gradle görevi: `:app:exportReleaseApk`

Paylaşılacak dosya: `releases/Odedim-1.0.1.apk`.

Sürüm adı `app/build.gradle.kts` dosyasındaki `releaseVersionName` değerinden alınır.
Yeni sürümde bu değeri ve `versionCode` değerini artırın. Ayarlar ekranındaki sürüm
bilgisi otomatik güncellenir. Çıktı dosyasının adı da sürüm adına göre değişir.

## İmzalama anahtarı

- Anahtar: `.signing/odedim-release.p12`
- Anahtar adı (alias): `odedim`
- Anahtar ve depo şifresi: `.signing/keystore-password.txt`

Bu dosyalar GitHub deposunda bulunmaz.

Bu klasörü güvenli bir yere yedekleyin. Anahtar ve şifre sonraki sürümleri
imzalamak için gereklidir; dağıtım APK'sının içine eklenmezler.
`.signing` ve `releases` klasörleri Git dışında tutulur.
Başkasına yalnızca APK'yı gönderin.

## Mevcut test kurulumu

Android Studio'dan kurulmuş debug uygulaması farklı bir anahtarla imzalıdır.
Release APK aynı uygulama kimliğini kullanır; farklı imza nedeniyle debug
kurulumunun üzerine doğrudan güncellenemez. Uygulamayı kaldırmak yerel ödeme
verilerini siler. Verileri korumak için mevcut test kurulumunu kaldırmayın;
release APK yeni telefonlarda kurulabilir.

Telefona otomatik kurulum yapılmaz.
