# Dagmar NG (Android)

Nativní Android aplikace pro zaměstnance portálu Dagmar (docházka).
Aplikace nepoužívá WebView jako UI. Data se načítají přes API.

Funkce:
- registrace zařízení (`/api/v1/instances/register`)
- polling stavu (`/api/v1/instances/{id}/status`)
- získání tokenu (`/api/v1/instances/{id}/claim-token`)
- docházka zaměstnance:
  - GET `/api/v1/attendance?year=YYYY&month=MM`
  - PUT `/api/v1/attendance`
- kontrola nové verze při startu (volitelný endpoint na serveru)
- splash: černá obrazovka + logo

Konfigurace serveru:
- kanonická doména je napevno `https://dagmar.hcasc.cz` (stejně jako web).

Update manifest (pokud existuje):
Aplikace zkusí postupně stáhnout JSON z:
- `https://dagmar.hcasc.cz/android-version.json`
- `https://dagmar.hcasc.cz/download/dochazka-dagmar-ng.json`
- `https://dagmar.hcasc.cz/download/dochazka-dagmar.json`

Minimální formát:
```json
{
  "version_code": 3,
  "version_name": "2.0.1",
  "apk_url": "https://dagmar.hcasc.cz/download/dochazka-dagmar.apk",
  "message": "Volitelná zpráva"
}
```

Poznámka k Gradle wrapperu:
V tomto ZIPu není přiložen `gradle-wrapper.jar`. Android Studio si jej běžně doplní při prvním syncu
(vyžaduje internet pro stažení Gradle distribuce podle `gradle-wrapper.properties`).
