# Monitor baterie pentru Sony Xperia 1 V

Aplicație Android (Android 8.0 sau mai nou, testată ca țintă pe Android 15) care arată în timp real starea bateriei și păstrează un istoric cu grafice.

## Ecranele aplicației

- **Acasă:** nivelul bateriei, starea, timpul rămas estimat (sau timpul până la 100% la încărcare), temperatura, puterea, curentul, tensiunea și durata de viață estimată a bateriei.
- **Detalii:** toate valorile citite de la sistem, grupate pe energie, capacitate și informații despre baterie.
- **Istoric:** grafice pentru nivel, temperatură, tensiune și capacitate, pe 24 de ore, 7 zile sau 30 de zile, cu statistici.
- **Setări:** capacitatea nominală (implicit 5000 mAh), luna achiziției, scurtături către setările bateriei și ștergerea istoricului.

Interfața e în limba română și urmează tema deschisă sau închisă a telefonului.

## Ce afișează

- nivel, stare de încărcare și sursă (AC, USB, wireless)
- temperatură, tensiune, curent și putere
- capacitate rămasă și capacitate totală estimată, cu mediană calculată din istoric
- sănătate estimată față de capacitatea nominală (implicit 5000 mAh, se poate schimba în aplicație)
- numărul de cicluri, sănătatea raportată de sistem, politica de încărcare, data fabricației și prima utilizare, **dacă telefonul le oferă** (Android 14+ are API-urile, dar producătorii nu sunt obligați să le completeze)
- grafice pentru nivel, temperatură, tensiune și capacitate, pe 24 de ore, 7 zile sau 30 de zile

Aplicația salvează o măsurătoare la aproximativ 15 minute, chiar dacă nu e deschisă. Datele rămân doar pe telefon și aplicația nu cere acces la internet.

## Instalare pe telefon

1. Deschide pagina **Releases** a acestui repository: <https://github.com/ulphaleph47/-xperia-battery-monitor/releases/latest>
2. Descarcă fișierul `xperia-battery-monitor.apk`.
3. Deschide-l din notificarea de descărcare sau din aplicația Fișiere. La prima instalare, Android îți cere să permiți instalarea din surse necunoscute pentru browserul folosit. Permite și apoi apasă **Instalează**.
4. Pentru ca înregistrarea periodică să nu fie oprită, mergi la Setări → Aplicații → Monitor baterie → Baterie și alege **Nerestricționat**.

Versiunile noi se instalează peste cea veche, fără dezinstalare, pentru că toate sunt semnate cu aceeași cheie.

## Cum se compilează

Fiecare modificare pe ramura `main` pornește automat compilarea (GitHub Actions) și publică un APK nou la Releases. Local, e nevoie de JDK 17, Android SDK 35 și Gradle 8.10: `gradle assembleRelease`.

Cheia de semnare din folderul `keystore/` este una personală, publică în repository. E potrivită pentru o aplicație de uz propriu și nu pentru publicarea în magazine.
