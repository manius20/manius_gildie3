# manius_gildie 2.0 — regiony administracyjne, BlueMap i prefiks S16 SMP 2

## WAŻNE: nie kasuj zapisanych państw

Wersja 2.0 zachowuje **wewnętrzną nazwę `S16Countries` w `plugin.yml`** z premedytacją.
Dzięki temu działa na danych w **`plugins/S16Countries/`**, nawet gdy pobrany plik JAR
nazywa się `manius_gildie-2.0.0.jar`. NIE przenoś danych do folderu `manius_gildie`.

### Bezpieczna aktualizacja na IceHost

1. Zatrzymaj serwer (nie używaj `/reload`).
2. Zrób kopię **CAŁEGO** `plugins/S16Countries/`, zwłaszcza `data.yml`, `currency.yml`,
   `config.yml`, `messages.yml`, `roles.yml`, `gui.yml` i nowego `regions.yml` (jeśli istnieje).
   Najlepiej pobierz również pełny backup świata z panelu IceHost.
3. W GitHub wejdź do aktualnego repozytorium → Code → Add file → Upload files.
   Wgraj pliki z rozpakowanego ZIP zachowując ścieżki, w tym `.github/workflows/build.yml`.
   Najłatwiej zrobić to przez GitHub Desktop: File → Add local repository, Commit i Push.
4. W repozytorium: **Actions → manius_gildie - buduj JAR → Run workflow**.
5. Po zielonym statusie pobierz **Artifacts → manius_gildie-JAR**, rozpakuj i odszukaj
   `manius_gildie-2.0.0.jar`.
6. W IceHost: `plugins/` — usuń lub przenieś poza folder `plugins` **stary JAR tego
   samego pluginu**. Nie zostawiaj równocześnie starego i nowego pluginu.
   **Nie usuwaj folderu `plugins/S16Countries`!**
7. Wgraj `manius_gildie-2.0.0.jar` do `plugins/`.
8. W `plugins/S16Countries/config.yml` dopisz nowe sekcje z punktu poniżej (jeśli ich brak).
   W istniejącym `bluemap:` dopisz `refresh-seconds`, `show-top`, `max-listed-members`.
9. Uruchom serwer. Sprawdź w konsoli błędy, sprawdź `/gildia lista`, `/gildia admin region lista`.
10. W razie problemów: wyłącz serwer, przywróć stary JAR i kopię folderu z punktu 2.

**Uwaga:** nowe wartości z JAR nie zawsze fizycznie dopisują się do już istniejącego
`config.yml` w IceHost. Nowe opcje są odczytywane z wartościami domyślnymi, ale jeżeli
chcesz nimi zarządzać na IceHost, wklej je ręcznie we właściwe miejsce.

### Nowe ustawienia `config.yml`

```yaml
chat:
  prefix: '&9&lS16 SMP 2 &8» &r'

admin-regions:
  enabled: true
  wand-material: BLAZE_ROD
  max-radius-chunks: 32
  max-region-area-chunks: 4096
  particle-interval-ticks: 10
  particle-duration-seconds: 8

stats:
  count-kills: true
```

**W istniejącej sekcji `bluemap:`** (nie twórz drugiej) dodaj:

```yaml
  refresh-seconds: 30
  show-top: 3
  max-listed-members: 30
```

W `general.enabled-worlds` zostaw nazwę świata `S16_wojny_1`, którą sprawdziłeś.

### Tworzenie regionów administracyjnych

**Różdżka i narożniki** — wymagane OP lub `s16countries.admin` oraz `s16countries.region.admin`:

```text
/gildia admin region narzedzie
```

Uderz **LPM** pierwszy blok, potem **PPM** drugi blok. Następnie:

```text
/gildia admin region zaznacz-chunki spawn
```

Zabezpiecza pełne chunki zaznaczenia. Możesz też użyć dokładnego prostokąta bloków:

```text
/gildia admin region zaznacz-bloki dungeon1
```

**WAŻNE:** nawet dokładny region bloków zabrania claimowania całego chunku, jeśli choćby
jeden jego blok przecina region. Wysokość Y nie ma znaczenia — administracyjne regiony
ograniczają całe pionowe kolumny.

**Promień od pozycji admina**, liczony w chunkach:

```text
/gildia admin region promien spawn 5
```

Oznacza chunk pod graczem i 5 chunków w każdą stronę — łącznie 11×11 chunków.

**Inne komendy:**

```text
/gildia admin region pokaz spawn
/gildia admin region lista
/gildia admin region usun spawn
/gildia admin region pomoc
/gildia admin reload
```

Regiony zapisują się automatycznie w `plugins/S16Countries/regions.yml`.
Można je edytować przy **wyłączonym serwerze**, potem uruchomić serwer ponownie.
Komenda `reload` odczytuje też edytowane `regions.yml`.

Nowy region NIE powstanie, jeśli przecina claim istniejącej gildii albo inny region
administracyjny. Nie usuwa i nie odbiera istniejących claimów.

Na admin-regionach **każdy może normalnie budować, niszczyć, używać skrzyń i walczyć**,
o ile inny plugin tego nie blokuje. Ochronę bloków na spawnie/dungeonach konfiguruj
w WorldGuard (sam WorldEdit nie nakłada ochrony).

Poza admin-regionami teren wolny jest wolny do budowania, a **claimy gildii nadal
chronią budowanie, interakcje itp.** zgodnie z `claims.protect-*`.

### BlueMap: statystyki i TOP gildii

Kliknięcie na chunk gildii wyświetla:
- nazwę gildii, pozycję w rankingu terenowym i liczbę posiadanych chunków;
- liczbę zabójstw (naliczanych od zainstalowania aktualizacji; zabicie członka
  **tej samej gildii** nie jest zaliczane);
- liczbę członków oraz zielone nicki online / czerwone nicki offline;
- TOP 3 gildii według powierzchni (zmienisz `bluemap.show-top`).

Informacje są aktualizowane co 30 sekund (albo częściej przy zmianie claimów i zabójstwach).
**Nowe statystyki zabójstw** są dopisywane jako `kills` do już istniejących wpisów
w `data.yml`; inne stare dane pozostają bez zmian.

### Wiadomości

Wszystkie wiadomości emitowane bezpośrednio przez manius_gildie dostają prefiks:

`§9§lS16 SMP 2 §8» §r`

Zmień go przez `chat.prefix` w `config.yml`. Wiadomości innych pluginów i vanilli
są niezależne od manius_gildie.

### Testy po instalacji

- `/gildia info` — istniejące gildie i chunki powinny pozostać.
- `/gildia admin region promien spawn_test 1` **poza istniejącymi claimami**.
- `/gildia zaloz test123` w admin-regionie — powinno odmówić.
- `/gildia claim` w admin-regionie — powinno odmówić, a nie pobrać walutę.
- Sprawdź niszczenie zwykłego bloku na regionie admina — powinno działać
  (chyba że WorldGuard blokuje ten obszar).
- `/gildia admin region usun spawn_test` — usuń test.
- Otwórz BlueMap i kliknij chunk gildii; po minucie sprawdź aktualizację online/offline.

### Status weryfikacji

Projekt zawiera testy JUnit i workflow `mvn clean verify` na Java 25.
W środowisku przygotowania archiwum nie było Java 25 ani zależności Paper/BlueMap,
dlatego **nie potwierdzono jeszcze pomyślnego zbudowania JAR**. Test kompilacji
zostanie przeprowadzony przez GitHub Actions po wysłaniu projektu.
