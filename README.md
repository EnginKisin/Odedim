# Ödedim

Ödedim; faturaları, abonelikleri ve düzenli ödemeleri telefonda takip etmek için
geliştirilmiş, tamamen çevrimdışı çalışan bir Android uygulamasıdır.
Ödemelerin ne zaman yapılacağını hatırlatır ve ödenen kayıtların geçmişini tutar.

Kişisel kullanım için sade bir ödeme takibi sunar. Hesap açmayı veya internet
bağlantısını gerektirmez; veriler Room ile cihazda saklanır.

## Ekran görüntüleri

Örnek verilerle uygulamadan alınan ekran görüntüleri. Listede farklı tutar, tarih
ve tekrar kurallarına sahip 7 ödeme bulunur: Kredi, Bina aidatı, Vodafone,
Turkcell, Elektrik, Netflix ve İnternet.

| Ödeme listesi | Ödeme ekleme | Filtreleme |
| --- | --- | --- |
| <img src="docs/screenshots/home.png" width="240" alt="Yedi kayıtlı ödeme bulunan ana ekran ve bildirim sayacı"> | <img src="docs/screenshots/add-payment.png" width="240" alt="Yeni ödeme oluşturma, tekrar kuralı, tarih ve saat seçimi"> | <img src="docs/screenshots/filters.png" width="240" alt="Para birimi, tutar aralığı ve ödeme tarihine göre filtreleme"> |

| Özet | Bildirimler | Ayarlar |
| --- | --- | --- |
| <img src="docs/screenshots/summary.png" width="240" alt="Son 12 ayın ödeme özeti ve aylık grafik"> | <img src="docs/screenshots/notifications.png" width="240" alt="Farklı ödemeler için gönderilen hatırlatmalar"> | <img src="docs/screenshots/settings.png" width="240" alt="Ödeme hatırlatmaları, bildirim sesi ve maaş günü ayarları"> |

## Neler yapabilirsiniz?

- Ödeme ekleme, düzenleme ve silme; TL, USD ve EUR desteği.
- 30 günde bir, her ay belirlenen günde veya maaş gününde tekrar eden ödemeler.
- İlk vadeyi seçme veya daha önce yapılmış bir ödemeyi kaydetme.
- **Ödedim** ile sonraki vadeyi hesaplama; geçmişten yalnızca son ödemeyi geri alma.
- İsimle arama; para birimi, tutar ve tarih aralığına göre filtreleme ve sıralama.
- 1, 3, 6, 12 ve 24 aylık grafikler; para birimleri ayrı hesaplanır.
- Ödeme zamanından 1 gün, 2 saat ve 30 dakika önce, ayrıca ödeme zamanı geldiğinde bildirim.
- Son 10 bildirimi uygulama içinde görme ve temizleme.

Her ödeme için son **24 ödeme kaydı** saklanır; bu sınır 24 takvim ayı anlamına gelmez.
Daha eski kayıtlar otomatik silinir. Hatırlatmalar Ayarlar'dan açılıp kapatılabilir.

## Kullanılan teknolojiler

Kotlin, Jetpack Compose, Material 3, Room ve Android'in yerel bildirim/zamanlama API'leri.
Backend, Firebase, kullanıcı girişi veya harici servis entegrasyonu bulunmaz.

## Çalıştırma

1. Projeyi Android Studio'da açın ve Gradle senkronizasyonunu tamamlayın.
2. Android SDK 37'nin kurulu olduğundan emin olun.
3. Android 8.0 (API 26) veya üzeri bir cihaz/emülatör seçip **Run** ile çalıştırın.

Debug sürümü için release imzalama anahtarı gerekmez.
Hatırlatmalar için uygulamanın Ayarlar ekranındaki bildirim ve zamanlama izinlerini tamamlayın.

Unit testler: `./gradlew :app:testDebugUnitTest`
