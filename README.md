# Counter POS (Android, offline, USB thermal printer)

POS web app ek WebView ke andar chalta hai (app/src/main/assets/pos.html).
USB printing seedha Android USB API se hoti hai: koi RawBT ya doosra app nahi, koi third-party library nahi.

## APK banane ke steps (ek baar)
1. Computer par Android Studio install karein (free).
2. File > Open > is `CounterPOS` folder ko chunein. Pehli baar Gradle sync mein kuch minute lagte hain (internet chahiye).
3. Menu: Build > Build Bundle(s) / APK(s) > Build APK(s).
4. APK yahan milti hai: app/build/outputs/apk/debug/app-debug.apk
5. Is APK ko tablet/phone par copy karke install karein (Settings mein "Install unknown apps" allow karein).

## Printer chalane ke steps
1. RP326 ko OTG cable se tablet se jodein, printer ON karein.
2. Android pooch sakta hai "Allow Counter POS to access the USB device?" -> "Always open Counter POS" tick karke OK.
3. App mein Setup (admin) > Test print.

## Dhyan
- Data app ke andar save hota hai. App uninstall karne se pehle Setup > Share backup karein.
- Agar sync mein dikkat aaye: Android Studio mein naya "Empty Views Activity" project (package com.counterpos, Kotlin, minSdk 21) banayein aur is folder ki files copy karein.

## Android Studio ke bina APK (GitHub se, free)
1. github.com par free account banayein.
2. New repository banayein (naam: CounterPOS).
3. "uploading an existing file" se is folder ki SAARI files upload karein (.github folder samet).
   Agar .github upload na ho: Add file > Create new file, naam `.github/workflows/build.yml` likhein aur file ka text paste karein.
4. Actions tab > "Build APK" > Run workflow. 3-6 minute mein build ho jata hai.
5. Build ke andar "Artifacts" mein CounterPOS-apk download karein, zip khol kar app-debug.apk install karein.
