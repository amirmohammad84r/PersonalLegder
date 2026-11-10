# Personal Ledger

اپلیکیشن مدیریت مالی شخصی برای **Android** و **iOS** با Kotlin Multiplatform و Compose Multiplatform.

## قابلیت‌ها

- **خانه:** موجودی کل، درآمد/هزینهٔ ماه جاری، تراکنش‌های اخیر
- **تراکنش‌ها:** افزودن/ویرایش دستی یا از طریق paste پیامک بانکی، جست‌وجو و فیلتر
- **حساب‌ها و دسته‌بندی‌ها:** مدیریت کامل با حذف امن وابستگی‌ها
- **اهداف مالی:** هدف پس‌انداز، تخصیص مبلغ از حساب، پیشرفت و بایگانی
- **آمار:** نمودار دوره‌ای روزانه/هفتگی/ماهانه/سالانه با تقویم شمسی یا میلادی
- **دستیار هوشمند:** اتصال مستقیم دستگاه به OpenAI، DeepSeek، GapGPT یا Google Gemini؛ محاسبهٔ مالی در اپ و توضیح توسط مدل
- تم روشن / تیره / سیستم، رابط فارسی و راست‌چین

## اجرا

### Android

```bash
./gradlew :androidApp:assembleDebug
```

در Android Studio ماژول `androidApp` را Run کنید.

### iOS

پوشهٔ `iosApp` را در Xcode باز کنید و روی دستگاه یا شبیه‌ساز Run بگیرید.

پیش‌نیاز: macOS + Xcode و یک JDK 17+ برای اسکریپت `Compile Kotlin Framework`.  
Xcode معمولاً `JAVA_HOME` ترمینال را نمی‌بیند؛ اسکریپت `iosApp/scripts/compile-kotlin-framework.sh` به‌ترتیب این‌ها را امتحان می‌کند: `JAVA_HOME`، `java.home` در `local.properties`، `/usr/libexec/java_home`، سپس JBR داخل Android Studio.

```properties
java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home
```

### تست

```bash
./gradlew :shared:testAndroidHostTest
```

## ساختار

```
shared/          منطق مشترک KMP (domain / data / presentation / DI)
androidApp/      نقطهٔ ورود Android
iosApp/          نقطهٔ ورود SwiftUI → Compose Shared
docs/            یادداشت‌های مستند (مثل منابع parser پیامک)
```

معماری: MVVM + لایه‌بندی سبک Clean (UI → ViewModel → UseCase → Repository → Local store).

## تصمیم‌های مهم

| موضوع | انتخاب |
| --- | --- |
| واحد پول | ریال (`Long` / `amountRial`) |
| تقویم | شمسی یا میلادی از تنظیمات کاربر |
| ذخیره‌سازی | JSON محلی + Multiplatform Settings؛ کلید API در حافظهٔ رمزنگاری‌شده |
| دستیار AI | بدون backend؛ درخواست مستقیم HTTPS به provider با کلید کاربر |
| پیامک بانکی | paste محلی؛ بدون ارسال به شبکه |

## سازگاری ابزار

- AGP `9.0.0`
- Compose Multiplatform `1.10.3`
- Kotlin `2.3.20`
- Gradle `9.5.1`

## کلون

```bash
git clone git@github.com:amirmohammad84r/PersonalLegder.git
```
