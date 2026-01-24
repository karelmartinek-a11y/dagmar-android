# DAGMAR Android (Kotlin + WebView)

Tato Android aplikace je minimalistický „wrapper“ nad webovou aplikací DAGMAR.

- Doména (kanonická): **https://dagmar.hcasc.cz**
- WebView URL: **https://dagmar.hcasc.cz/app**
- Distribuce mimo Google Play: APK se stahuje z webu: **https://dagmar.hcasc.cz/download/dochazka-dagmar.apk**
- Zakázané externí služby: Firebase / FCM / Play Integrity / Auth0 (nepoužívá se)

## 1) Požadavky na build (vývojářský stroj)

Doporučeno buildit na vývojářském PC (Linux/macOS/Windows). Na serveru to není potřeba.

- JDK 17
- Android SDK + build tools
- Gradle (wrapper je součástí projektu)

Pozn.: Během buildu je povoleno stahování závislostí z Maven/Gradle repozitářů. V runtime aplikace nevyžaduje žádné externí služby.

## 2) Konfigurace

Aplikace je pevně zacílena na:

- `BASE_URL = https://dagmar.hcasc.cz`
- `WEBVIEW_START_URL = https://dagmar.hcasc.cz/app`

Tyto hodnoty se **nemají** měnit pro produkci.

## 3) Co aplikace ukládá lokálně

- `instance_id` (UUID z backendu)
- `instance_token` (Bearer token vydaný po aktivaci adminem)

Ukládá se do **EncryptedSharedPreferences**.

Docházka ani historie docházky se lokálně **nesmí** persistovat.

## 4) Aktivace zařízení (životní cyklus instance)

1. První spuštění aplikace:
   - Aplikace vygeneruje `device_fingerprint` (UUID) a zavolá:
     - `POST https://dagmar.hcasc.cz/api/v1/instances/register`
   - Dostane `instance_id` se stavem `PENDING`.

2. Dokud je zařízení `PENDING`:
   - WebView zobrazí obrazovku **„Zařízení není aktivováno“**.
   - Aplikace periodicky kontroluje (polling) vydání tokenu:
     - `POST https://dagmar.hcasc.cz/api/v1/instances/{instance_id}/claim-token`

3. Admin aktivuje zařízení ve web adminu:
   - nastaví `display_name`
   - backend přepne instance na `ACTIVE` a umožní vydat token

4. Po aktivaci:
   - `claim-token` vrátí `instance_token`
   - aplikace token uloží a WebView se odemkne pro docházku

5. Pokud admin instanci revokuje (`REVOKED`):
   - token přestane fungovat a aplikace zůstane na obrazovce deaktivace

## 5) Release build APK (produkční)

### 5.1 Nastavení versionName / versionCode

V `dagmar/android/app/build.gradle.kts` nastav:

- `versionCode` (inkrementovat při každém release)
- `versionName` (např. `1.0.0`)

### 5.2 Vytvoření keystore (jednorázově)

Na build stroji:

```bash
cd dagmar/android

# vytvoří release keystore (uchovej bezpečně!)
keytool -genkeypair \
  -v \
  -keystore dagmar-release.jks \
  -alias dagmar \
  -keyalg RSA \
  -keysize 4096 \
  -validity 3650
```

Doporučení:
- `dagmar-release.jks` NEcommitovat do repozitáře.
- Hesla držet v bezpečném password manageru.

### 5.3 Build release (Gradle)

```bash
cd dagmar/android
./gradlew clean :app:assembleRelease
```

Výstup bude typicky:

- `dagmar/android/app/build/outputs/apk/release/app-release.apk`

### 5.4 Zipalign + apksigner (doporučeno)

Najdi `zipalign` a `apksigner` v Android SDK (Build Tools). Příklad (uprav cesty):

```bash
APK_IN="app/build/outputs/apk/release/app-release.apk"
APK_ALIGNED="app-release-aligned.apk"
APK_OUT="dochazka-dagmar.apk"

# zipalign
~/Android/Sdk/build-tools/34.0.0/zipalign -v 4 "$APK_IN" "$APK_ALIGNED"

# sign
~/Android/Sdk/build-tools/34.0.0/apksigner sign \
  --ks dagmar-release.jks \
  --ks-key-alias dagmar \
  --out "$APK_OUT" \
  "$APK_ALIGNED"

# verify
~/Android/Sdk/build-tools/34.0.0/apksigner verify --verbose "$APK_OUT"
```

Pozn.: Pokud používáš signing přímo v Gradlu, stále je validní provést `apksigner verify`.

## 6) Nahrání APK na server

APK musí být dostupné přes:

- **`https://dagmar.hcasc.cz/download/dochazka-dagmar.apk`**

Na serveru je deterministická cesta:

- `/var/www/dagmar/download/dochazka-dagmar.apk`

Příklad nahrání (z build stroje):

```bash
# na build stroji (lokálně)
scp dochazka-dagmar.apk root@TVUJ_SERVER:/tmp/

# na serveru
sudo mkdir -p /var/www/dagmar/download
sudo mv /tmp/dochazka-dagmar.apk /var/www/dagmar/download/dochazka-dagmar.apk
sudo chown -R dagmar:dagmar /var/www/dagmar
sudo chmod 0644 /var/www/dagmar/download/dochazka-dagmar.apk
```

## 7) Ověření stažení

Na serveru nebo lokálně:

```bash
curl -I https://dagmar.hcasc.cz/download/dochazka-dagmar.apk
```

Zkontroluj:
- HTTP 200
- smysluplný `Content-Type` (typicky `application/vnd.android.package-archive`)
- velikost odpovídá APK

## 8) Troubleshooting

- Aplikace stále ukazuje „Zařízení není aktivováno“:
  - přihlas se do adminu ve webu DAGMAR, zkontroluj že instance je `ACTIVE`
  - zkontroluj, že `claim-token` není rate-limited (429) a že běží backend
- Nelze načíst web v WebView:
  - ověř DNS pro `dagmar.hcasc.cz`
  - ověř certifikát a Nginx konfiguraci
  - ověř, že backend poslouchá na `127.0.0.1:8101` a `/api` proxy funguje
