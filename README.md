# manius_gildie 2.0 — aktualizacja S16 SMP 2

**Najpierw przeczytaj [`AKTUALIZACJA-ICEHOST.md`](AKTUALIZACJA-ICEHOST.md)!**
Nowości: admin-regiony bez claimów, rangowanie chunków na BlueMap, zabójstwa PvP,
online/offline w okienku gildii, nowy prefiks wszystkich komunikatów.
Nazwa pluginu wewnętrznie pozostaje `S16Countries`, aby nie utracić `data.yml`.

---

# 🌍 S16Countries 1.0.0 — S16 SMP 2

Autorski plugin państw/gildii pod **Paper 26.2 i Java 25**.

## Funkcje

- Państwa: tworzenie, rozwiązanie, zapraszanie, dołączanie, opuszczanie, wyrzucanie, przekazywanie lidera.
- Własne role i uprawnienia w `roles.yml`, komendy `/gildia rola` i `/gildia przekaz`.
- Claimy 16×16 kupowane za fizyczne przedmioty (bez Vault i salda); tylko sąsiedztwo bokiem; ochrona terenu.
- Wzorzec przedmiotu ustawiany w GUI przez administratora; `currency.yml` powstaje automatycznie.
- Dowolne PvP **przez cały czas**, także poza wojnami. `/gildia pvp` przełącza wyłącznie obrażenia w ramach jednej gildii. PvP musi być dozwolone w `server.properties`.
- Wojny: przygotowanie, czas trwania, pokój za obopólną zgodą, podbój chunków przez stanie na terenie, obrońcy zatrzymują licznik.
- Sojusze (obopólna akceptacja), ranking państw, lista wojen, siedziby, teleportacja, GUI, mapa ASCII chunków na czacie.
- Opcjonalne oznaczanie claimów na mapie **BlueMap** (wymaga osobnego pluginu BlueMap).
- Konfiguracje: `config.yml`, `roles.yml`, `gui.yml`, `messages.yml`.
- Trwały zapis danych: `data.yml` (automatycznie) z atomową zamianą pliku i kopią podczas startu.

**Uwaga:** to pierwsza wersja projektu. Przed włączeniem na publicznym serwerze wykonaj kopię świata, przetestuj działania z kilkoma graczami, sprawdź logi i wyniki kompilacji w GitHub Actions. Nie jest to zamiennik wszystkich funkcji Towny lub Lands (np. nie ma podatków, obszernych traktatów, oblężeń opartych na działach ani osobnego narzędzia do edycji mapy). Nie gwarantuje obsługi wszystkich możliwych mechanizmów griefingu — testuj zwłaszcza TNT, redstone i interakcje z innymi pluginami.

## 1. Jak wrzucić kod na GitHub — GitHub Desktop (najłatwiej)

1. Pobierz `S16Countries-projekt.zip` od ChatGPT.
2. Rozpakuj ZIP. W środku ma być folder `S16Countries`; w nim **bezpośrednio** `pom.xml`, `README.md`, `.github`, `src`. Nie wrzucaj ZIP jako pliku źródłowego do repozytorium.
3. Zainstaluj **GitHub Desktop** z https://desktop.github.com/ i zaloguj się do konta GitHub.
4. Kliknij `File` → `Add local repository...` → `Choose...`, wskaż rozpakowany folder `S16Countries`.
5. Jeśli GitHub Desktop napisze, że to nie jest repozytorium, kliknij `Create a repository here` i zatwierdź utworzenie repo. Wybierz `S16Countries` jako nazwę.
6. Na zakładce `Changes` wpisz np. `Pierwsza wersja S16Countries`, kliknij `Commit to main`.
7. Kliknij `Publish repository`. Wybierz widoczność `Private` lub `Public` i zatwierdź.
8. Na github.com przejdź do repo `S16Countries` → zakładka `Actions` → workflow **S16Countries - buduj JAR**. Jeżeli potrzebne, zezwól na workflows przyciskiem `I understand my workflows, go ahead and enable them`.
9. Kliknij `Run workflow` (lub poczekaj na automatyczne uruchomienie po push). Po zielonym ✅ otwórz wykonanie → na dole w sekcji `Artifacts` kliknij `S16Countries-JAR`.
10. Rozpakuj pobrane archiwum artefaktu. Uzyskasz **`S16Countries-1.0.0.jar`**. **To ten plik** trafia na serwer, NIE źródłowy ZIP projektu.

Jeśli zmienisz kod przez GitHub Desktop, wykonaj `Commit to main` → `Push origin`, a workflow wygeneruje nowy artefakt.

### Dokładne ścieżki w repozytorium

```text
S16Countries/
├── .github/
│   └── workflows/
│       └── build.yml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── pl/s16/countries/
│   │   │       ├── S16Countries.java
│   │   │       ├── GuildCommand.java
│   │   │       ├── Nation.java
│   │   │       ├── NationStore.java
│   │   │       ├── ClaimKey.java
│   │   │       ├── CurrencyService.java
│   │   │       ├── War.java
│   │   │       ├── WarService.java
│   │   │       ├── ProtectionListener.java
│   │   │       ├── MenuService.java
│   │   │       ├── BlueMapHook.java
│   │   │       └── ConfigFiles.java
│   │   └── resources/
│   │       ├── plugin.yml
│   │       ├── config.yml
│   │       ├── roles.yml
│   │       ├── gui.yml
│   │       └── messages.yml
│   └── test/java/pl/s16/countries/NationTopologyTest.java
├── pom.xml
└── README.md
```

Maven kompiluje kod z `src/main/java` i umieszcza konfiguracje z `src/main/resources` w wynikowym JAR. Plik workflow **musi** być pod `.github/workflows/build.yml` (kropka jest częścią nazwy).

## 2. IceHost.pl — instalacja

1. Zrób backup świata oraz folderu `plugins`.
2. Otwórz panel IceHost → **Parametry Startowe** → zmień obraz Dockera / wersję Javy na **Java 25**. IceHost podaje Java 25 dla Paper 26.1–26.2+ w swojej wiki: https://wiki.icehost.pl/serwery-minecraft/wersja-silnika/ . Jeżeli Paper 26.2 już startuje, środowisko powinno spełniać ten wymóg.
3. W IceHost wybierz Paper **26.2** (nie 1.21.x). Zweryfikuj wersję w logach konsoli serwera.
4. Zatrzymaj serwer przyciskiem **STOP**.
5. Otwórz **Pliki** → `plugins/` (jeśli katalog nie istnieje, utwórz go).
6. Kliknij **Wyślij na serwer**, wybierz `S16Countries-1.0.0.jar` pobrany z sekcji GitHub `Artifacts`.
7. Wróć do konsoli, uruchom serwer. Powinien pokazać informację `S16Countries gotowy`.
8. Po pierwszym starcie znajdziesz pliki:

```text
plugins/
├── S16Countries-1.0.0.jar
└── S16Countries/
    ├── config.yml
    ├── roles.yml
    ├── gui.yml
    ├── messages.yml
    ├── data.yml             # automatycznie po zapisaniu państwa
    ├── currency.yml         # po ustawieniu waluty przez admina
    └── backups/             # kopie data.yml podczas startu
```

9. W głównym katalogu serwera otwórz `server.properties`; ustaw **`pvp=true`**. Zatrzymaj i uruchom serwer po zmianie.
10. W `plugins/S16Countries/config.yml` zmień `general.enabled-worlds: [world]` na dokładną nazwę mapy, jeśli masz inną (np. `[earth]`).
11. W grze nadaj sobie OP lub permission `s16countries.admin`, a następnie wpisz **`/gildia admin waluta`**. W zwykłym ekwipunku kliknij wybrany item, żeby mieć go na kursorze, kliknij środkowy slot nr 13 w GUI, potem przycisk **ZATWIERDŹ WALUTĘ**. Przedmiot w Twoim ekwipunku **nie znika** przy ustawianiu wzorca.
12. Po ustawieniu waluty wykonaj próbę `/gildia zaloz Test`, `/gildia claim`, `/gildia mapa`, `/gildia pvp`.

## 3. Konfiguracja i edycja w panelu

- `config.yml` — limity, ceny, światy, wojny, PvP, czas przejęcia, zabezpieczenia, dom gildii, kolor granic BlueMap.
- `roles.yml` — nazwy ról i ich prawa (np. role `prezydent`, `zastepca`, `minister`, `obywatel`).
- `gui.yml` — nazwy, materiały, opisy, komendy kliknięć, rozmiary menu i sloty przycisków.
- `messages.yml` — wybrane komunikaty systemowe i prefiks. **Część tekstów pomocniczych jest obecnie zapisana w kodzie** (nie wszystkie łańcuchy są już przeniesione do YAML).
- `currency.yml` — dokładny item-wzorzec ustawiony przez GUI, możesz również zaktualizować go z poziomu gry. Nie podmieniaj ręcznie tagów ItemStack bez znajomości formatu Bukkit.
- `data.yml` — rzeczywisty stan świata gildii i wojen. Nie edytuj ręcznie, gdy serwer działa. Dla ręcznych zmian w danych wyłącz serwer, zrób backup, zmodyfikuj plik i uruchom serwer.

Po edycji `config.yml`, `roles.yml`, `gui.yml`, `messages.yml`, `currency.yml` użyj `/gildia admin reload`.

## 4. Lista komend

| Komenda | Opis |
|---|---|
| `/gildia` lub `/gildia menu` | Główne GUI |
| `/gildia pomoc` | Pomoc |
| `/gildia zaloz <nazwa>` | Utwórz gildie (koszt itemów) |
| `/gildia usun potwierdz` | Rozwiąż gildię (wymaga wcześniejszego `/gildia usun`) |
| `/gildia info [nazwa]`, `/gildia lista`, `/gildia ranking` | Informacje, państwa, top |
| `/gildia czlonkowie` | Lista członków i ról |
| `/gildia zapros <nick>` | Wyślij zaproszenie |
| `/gildia dolacz <nazwa>` | Dołącz po zaproszeniu |
| `/gildia opusc`, `/gildia wyrzuc <nick>` | Wyjście / wyrzucenie |
| `/gildia rola <nick> <rola>` | Zmień rolę |
| `/gildia przekaz <nick>` | Przekaż przywództwo |
| `/gildia claim`, `/gildia unclaim` | Kup / oddaj chunk |
| `/gildia mapa`, `/gildia teren` | Mini-mapa / informacje o bieżącym chunku |
| `/gildia pvp` | Przełącz PvP **wewnątrz gildii** |
| `/gildia wojna <nazwa>`, `/gildia wojny` | Wypowiedz wojnę / listuj wojny |
| `/gildia pokoj <nazwa>` | Zaproponuj / zaakceptuj pokój |
| `/gildia podbij` | Rozpocznij zdobywanie wrogiego chunka podczas wojny |
| `/gildia sojusz <nazwa>`, `/gildia sojusze` | Zaoferuj/zaakceptuj sojusz, sprawdź sojusze |
| `/gildia zerwijsojusz <nazwa>` | Zerwij sojusz |
| `/gildia ustawdom`, `/gildia dom` | Ustaw / odwiedź siedzibę |
| `/gildia admin waluta` | Otwórz GUI wyboru waluty |
| `/gildia admin reload` | Przeładuj ustawienia |
| `/gildia admin resetwaluta` | Reset wzorca waluty |
| `/gildia admin claim <nazwa>` | Nadaj chunk gildii (nadpisuje właściciela) |
| `/gildia admin unclaim` | Usuń claim pod nogami |
| `/gildia admin wojna <A> <B>` | Wymuś natychmiastową wojnę |
| `/gildia admin stopwojna <A> <B>` | Zatrzymaj wojnę |

Uprawnienia pluginu: `s16countries.use` (każdy), `s16countries.admin` (operator), `s16countries.bypass` (operator). Kto może wykonywać poszczególne komendy wewnątrz gildii, ustawiasz w `roles.yml`.

## 5. Zasady waluty

Przedmiot jest sprawdzany przez Bukkit `ItemStack#isSimilar()`: liczy się nie tylko typ, ale i metadata (np. nazwa, enchanty, atrybuty, komponenty). Zwykły DIAMENT ustawiony jako wzorzec oznacza, że płaci się normalnymi diamentami bez niestandardowych metadata.

Płacone przedmioty są pobierane **ze standardowych 36 slotów ekwipunku**. Nie są pobierane z zbroi, off-hand, Ender Chest ani skrzynek.

Przykładowe ustawienia:

```yaml
currency:
  create-cost: 64
  war-declare-cost: 32
  capture-cost: 16
claims:
  base-cost: 12
  per-owned-chunk: 2
```

Przy tych parametrach 1. dodatkowy claim kosztuje `12 + 1×2 = 14` przedmiotów (pierwszy chunk jest przydzielany przy tworzeniu, jeśli `auto-claim-on-create: true`).

## 6. BlueMap — granice na stronie

1. Pobierz osobno kompatybilny z Paper 26.2 plugin **BlueMap**, np. jego wydanie `paper` wspierające 26.2: https://modrinth.com/plugin/bluemap/versions?g=26.2
2. Wgraj plik BlueMap `.jar` do `plugins/` obok S16Countries, uruchom BlueMap i skonfiguruj jego renderowanie zgodnie z instrukcją BlueMap.
3. Serwer/hosting musi pozwalać na dostęp do serwera WWW BlueMap (oddzielny port albo reverse proxy). Adres mapy nie powstaje automatycznie z tego pluginu.
4. Kiedy oba pluginy są uruchomione, S16Countries tworzy w BlueMap zestaw markerów `Granice państw S16`, aktualizowany po zmianie claimów.
5. Jeśli BlueMap nie jest zainstalowany, nadal działa `/gildia mapa`.

Integracja BlueMap jest opcjonalna i może wymagać dopasowania wersji API do wydania BlueMap, które używasz.

## 7. Testy przed otwarciem SMP

- Admin ustawia walutę w GUI, zamyka menu, otwiera ponownie i widzi dokładnie ten sam item.
- Nie da się kupić claimu ani stworzyć płatnego państwa bez odpowiednich przedmiotów.
- Claim może stykać się tylko bokiem; po odclaimowaniu teren nie rozdziela się na wyspy.
- Obcy gracz nie może niszczyć i stawiać bloków ani otwierać skrzyń na cudzym terenie.
- Gracze różnych gildii i gracze bez gildii biją się bez wojny (upewnij się, że `pvp=true`).
- Członkowie jednej gildii NIE zadają sobie obrażeń przed `/gildia pvp`; po tej komendzie zadają.
- Wojna nie pozwala na podbój przed zakończeniem okresu przygotowania.
- Obrońca w pobliżu zatrzymuje podbój; zwycięzca zostaje właścicielem chunka po opłaceniu kosztu.
- Restart serwera nie usuwa gildii, chunków, waluty ani wojen.
- BlueMap pokazuje tereny na właściwym świecie.

## 8. Ograniczenia pierwszej wersji

- Dane są w YAML, nie SQLite — przeznaczone raczej na małe/średnie SMP. Bardzo duża liczba claimów może spowalniać zapis i synchronizację z BlueMap.
- Nie ma wewnętrznego banku gildii ani pieniędzy na kontach, zgodnie z założeniem.
- Sojusze i zaproszenia oczekujące nie są zachowywane po restarcie; *zatwierdzone* sojusze i członkostwa są zachowywane.
- Nie ma automatycznego podnoszenia PvP, jeżeli inny plugin albo `server.properties` blokuje obrażenia.
- Nie wszystkie mechaniki innych popularnych pluginów (np. Dynmap, podatki, automatyczna gospodarka) są zawarte. Można je dodać w kolejnych wersjach.

## Licencja

Kod przykładowy udostępniony do wykorzystania i modyfikacji na serwerze S16 SMP 2.
