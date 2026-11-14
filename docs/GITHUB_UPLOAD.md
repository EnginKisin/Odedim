# Android Studio üzerinden GitHub'a yükleme

Projeyi GitHub'a bağlama ve ilk gönderim işlemini kendiniz yapabilirsiniz.
Bu hazırlıkta uzak depo oluşturulmadı, GitHub hesabına giriş yapılmadı ve push yapılmadı.

## 1. GitHub hesabını ekleyin

1. Mac'te **Android Studio → Settings → Version Control → GitHub** bölümünü açın.
2. **+ → Log In via GitHub** seçeneğine basın.
3. Açılan tarayıcıda kendi hesabınızla giriş yapıp IDE'ye izin verin.

Hesap parolasını veya erişim token'ını proje dosyalarına yazmayın.

## 2. Projeyi paylaşın

1. **VCS → Share Project on GitHub** menüsünü açın.
   Menü görünmezse **⌘ + Shift + A** ile **Share Project on GitHub** eylemini arayın.
   Proje zaten Git ile takip ediliyorsa eylem **Git → GitHub** altında bulunabilir.
2. Depo adını **Odedim** yazın.
3. Açıklama olarak şunu kullanabilirsiniz:
   `Tamamen çevrimdışı çalışan, Kotlin ve Jetpack Compose ile geliştirilmiş kişisel ödeme takip uygulaması.`
4. İlk paylaşım için **Private** seçebilirsiniz. Herkese açık paylaşmak isterseniz **Public** kullanın.
5. İlk commit için dosya listesi gösterilirse seçilen dosyaları kontrol edin.
   `.signing/`, `local.properties`, `releases/`, `build/`, anahtar ve şifre dosyaları listede olmamalı.
   Kaynak kodları, testler, Room şemaları, Gradle Wrapper, README ve `docs/` klasörü dahil edilmeli.
6. **Share** ve IDE'nin gösterdiği ilk commit/gönderim adımlarını tamamlayın.

İlk commit mesajı: `İlk sürüm: Ödedim 1.0.1`

## 3. Sonraki değişiklikler

Commit penceresinde değişiklikleri inceleyip bir commit mesajı yazın.
Ardından **Commit and Push** veya **Git → Push** ile GitHub'a gönderin.

Uygulamaya ait APK'lar kaynak kod deposuna dahil edilmez.
APK paylaşımını ileride GitHub'ın **Releases** bölümünden yapabilirsiniz;
imzalama anahtarlarını ve şifrelerini burada da paylaşmayın.

## Kaynaklar

- [IDE'ye GitHub hesabı ekleme](https://www.jetbrains.com/help/idea/set-up-a-github-account.html)
- [IDE'den GitHub'a proje paylaşma](https://www.jetbrains.com/help/idea/manage-projects-hosted-on-github.html)
