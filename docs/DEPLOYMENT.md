# Διαδικασίες του server (DEPLOYMENT)

Οι διαδικασίες της εφαρμογής στον server του γραφείου. Τις κάνετε εσείς,
στον server· το Claude Code δεν συνδέεται ποτέ εκεί (TASKS, Tasks 29–38,
Ε16). Το Task 31 έγραψε την εγκατάσταση· τα Tasks 32, 33 και 35 προσθέτουν
τα αντίγραφα ασφαλείας, την επαναφορά, τις ενημερώσεις και το ίδιο το
μηχάνημα.

## Η εφαρμογή στον server, από το μηδέν

Ένα `docker compose up -d` ξεκινά την PostgreSQL και την εφαρμογή (Task 31).
Ό,τι χρειάζεται ο server είναι στον φάκελο `deploy/` του repo· το image της
εφαρμογής χτίζεται στο μηχάνημα ανάπτυξης.

**Στον server πρέπει να υπάρχουν** (Task 35a): Docker Engine με το plugin του
compose· ένας φάκελος στον κρυπτογραφημένο δίσκο δεδομένων (στα
παραδείγματα, `/srv/insurance-office`)· journald μόνιμο (`/var/log/journal`),
ώστε τα logs να μένουν μετά από επανεκκίνηση.

### 1. Το image, στο μηχάνημα ανάπτυξης

```sh
./mvnw verify
docker buildx build --platform linux/amd64 --build-arg APP_VERSION=<έκδοση> \
  -t insurance-office:<έκδοση> --load .
```

*   Το image περιέχει το jar που μόλις έφτιαξε και έλεγξε το `./mvnw verify`·
    μέσα στο Docker δεν χτίζεται ούτε ελέγχεται τίποτα.
*   `--platform linux/arm64` για Raspberry Pi (Ε5).
*   Στον server φτάνει μέσα από το Tailscale, με τη διαδικασία του Task 33.

### 2. Τα αρχεία στον server

Αντιγράψτε τον φάκελο `deploy/` του repo στον server, π.χ. στο
`/srv/insurance-office/deploy`: το `compose.yaml`, τον φάκελο `initdb/` και το
`.env.example`. Τίποτα άλλο από τον κώδικα δεν χρειάζεται.

### 3. Το `.env`

```sh
cd /srv/insurance-office/deploy
cp .env.example .env
chmod 600 .env
openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-40; echo    # ο κωδικός της βάσης
```

Συμπληρώστε κάθε γραμμή· με μία κενή, το `docker compose` δεν ξεκινά και λέει
ποια λείπει.

| Γραμμή | Τι γράφει |
|---|---|
| `APP_VERSION` | Η έκδοση του image, όπως στο βήμα 1. |
| `DATA_DIR` | Ο φάκελος στον κρυπτογραφημένο δίσκο, π.χ. `/srv/insurance-office`· η βάση μπαίνει στο `postgresql/` από κάτω. |
| `DB_NAME`, `DB_USER` | Η βάση και ο ρόλος της εφαρμογής, π.χ. `insurance_office` και `insurance_app`. |
| `DB_PASSWORD` | Ο τυχαίος κωδικός της παραπάνω εντολής. Το αντίγραφό του φυλάσσεται όπως λέει το Ε17. |
| `APP_MEMORY` | Το όριο μνήμης της εφαρμογής, π.χ. `1g`· τα 3/4 είναι για τη Java. |

Μην προσθέσετε άλλες ρυθμίσεις της εφαρμογής στο `.env` (π.χ.
`SERVER_SERVLET_SESSION_TIMEOUT`): θα άλλαζαν σιωπηλά αποφάσεις που ο κώδικας
και τα tests του κρατούν (Task 29).

### 4. Η πρώτη εκκίνηση

```sh
docker compose up -d
docker compose ps       # postgres: healthy· app: Up
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/login     # 200
```

Στην πρώτη εκκίνηση:

*   η PostgreSQL φτιάχνει τον φάκελο δεδομένων, και το `initdb/` φτιάχνει τον
    ρόλο της εφαρμογής (`DB_USER`, όχι superuser) με τη βάση του
    (`DB_NAME`)· ο superuser μένει χωρίς κωδικό·
*   η εφαρμογή φτιάχνει τους πίνακες (Flyway) με τον δικό της ρόλο.

Τα `DB_NAME`, `DB_USER` και `DB_PASSWORD` περνούν στη βάση μόνο τότε. Το
όνομα της βάσης και του ρόλου δεν αλλάζουν μετά· ο κωδικός αλλάζει όπως
λέει το βήμα 8.

Η εφαρμογή ακούει μόνο στο `127.0.0.1:8080` του server, και η βάση πουθενά
έξω από το δίκτυο του Docker. Τα μηχανήματα του γραφείου τη βρίσκουν μέσα
από το `tailscale serve` (Task 35b), που της στέλνει το `https` και το
όνομα του server στα headers `X-Forwarded-Proto` και `X-Forwarded-Host`: έτσι
κάθε redirect μένει στη διεύθυνση https. Το cookie της σύνδεσης είναι πάντα
`Secure` στον server: το ορίζει το `compose.yaml`, και δεν είναι ρύθμιση του
`.env`, γιατί ο server δεν ανοίγει ποτέ χωρίς https.

Μετά από επανεκκίνηση του server, τα containers ξεκινούν μόνα τους μόλις
ξεκινήσει το Docker (`restart: unless-stopped`).

### 5. Οι λογαριασμοί

```sh
docker compose run --rm -it app --spring.profiles.active=create-user \
  --user.username=<όνομα> '--user.full-name=<ονοματεπώνυμο>' [--user.role=ΥΠΑΛΛΗΛΟΣ]
```

*   Χωρίς `--user.role` ο λογαριασμός είναι ΔΙΑΧΕΙΡΙΣΤΗΣ· με
    `--user.role=ΥΠΑΛΛΗΛΟΣ`, υπάλληλος.
*   Ο κωδικός ζητείται δύο φορές, χωρίς να φαίνεται (Task 22a). Το `-it`
    χρειάζεται: χωρίς τερματικό η εντολή σταματά.
*   Η ίδια εντολή, για λογαριασμό που υπάρχει, ορίζει νέο κωδικό.

### 6. Η εισαγωγή (Tasks 37 και 38)

```sh
docker compose run --rm -it -v /srv/insurance-office/import:/import:ro app \
  --spring.profiles.active=import \
  --import.customers-file=/import/<customers.xlsx> --import.archive-file=/import/<archive.xlsx>
```

*   Ο φάκελος των Excel μπαίνει στο container μόνο για ανάγνωση.
*   Η εφαρμογή τρέχει ως `nobody`: τα αρχεία πρέπει να διαβάζονται από όλους
    (`chmod 644`, ο φάκελος `755`). Γι' αυτό μένουν στον κρυπτογραφημένο δίσκο,
    και φεύγουν μετά τη μεταφορά (Task 38).
*   Η αναφορά γράφεται στην οθόνη και στο journal (βήμα 7).

### 7. Τα logs

```sh
journalctl -t insurance-office-app                 # η εφαρμογή
journalctl -t insurance-office-db                  # η PostgreSQL
journalctl -t insurance-office-app -f              # ζωντανά
journalctl -t insurance-office-app --since today
journalctl -t insurance-office-app | grep 'Κλείδωμα σύνδεσης'       # Task 22b
```

Τα logs μένουν στο journal του server και μετά από ενημέρωση, που
ξαναφτιάχνει τα containers. Το `docker compose logs` δείχνει μόνο το τωρινό
container. Οι ώρες είναι ώρα Ελλάδας.

### 8. Η βάση με το χέρι

```sh
docker compose exec postgres sh -c 'psql -U "$DB_USER" -d "$DB_NAME"'
```

Ως ο ρόλος της εφαρμογής, με ώρα Ελλάδας.

*   **Νέος κωδικός της βάσης:** μέσα στο `psql` γράψτε `\password`: ζητά τον
    νέο δύο φορές, χωρίς να φαίνεται και χωρίς να μένει στο ιστορικό. Μετά
    τον ίδιο στο `DB_PASSWORD` του `.env` (και στο αντίγραφο του Ε17), και
    `docker compose up -d`, που ξαναφτιάχνει τα containers με τον νέο.
*   **Ο superuser** (`docker compose exec -u postgres postgres psql`) μόνο για
    συντήρηση που ο ρόλος δεν μπορεί να κάνει. Δεν έχει κωδικό: κανένα άλλο
    container δεν συνδέεται ως αυτός, μόνο ένα shell μέσα στο container της
    PostgreSQL.

### 9. Σταμάτημα

```sh
docker compose down     # σταματά και σβήνει τα containers· τα δεδομένα μένουν
docker compose up -d    # ξανά
```

Τα δεδομένα είναι στο `DATA_DIR/postgresql` και δεν σβήνονται με το `down`.

## Νέος διαμεσολαβητής

Η εφαρμογή δεν έχει οθόνη για διαμεσολαβητές: τους φτιάχνει η εισαγωγή, και
μετά την έναρξη χρήσης ο developer προσθέτει έναν νέο με SQL (TASKS, Task 38,
Αποφάσεις). Η φόρμα συμβολαίου τον προσφέρει αμέσως, χωρίς επανεκκίνηση.

Μαζί του, στην ίδια συναλλαγή, γράφεται η γραμμή του `audit_log`, όπως θα
την έγραφε ο `AuditListener`: `CREATE`, `Intermediary`, κάθε στήλη με το
όνομά της (`id`, `full_name`, `registry_number`, `phone`, `email`, `active`,
τα κενά ως JSON `null`), και `user_id` κενό, όπως σε κάθε αλλαγή χωρίς
συνδεδεμένο χρήστη (CLAUDE.md, resolved conflict 6). Ελέγχθηκε απέναντι σε
γραμμή που έγραψε ο listener: ίδια κλειδιά, ίδιοι τύποι.

1.  Ανοίξτε `psql` στη βάση, ως ο ρόλος της εφαρμογής, μέσα από το container
    της PostgreSQL, ώστε η ώρα της γραμμής να είναι ώρα Ελλάδας (βήμα 8 της
    εγκατάστασης):

    ```sh
    docker compose exec postgres sh -c 'psql -U "$DB_USER" -d "$DB_NAME"'
    ```

2.  Ψάξτε αν υπάρχει ήδη, με άλλη γραφή ή ανενεργός, χωρίς τόνους και
    πεζά/κεφαλαία:

    ```sql
    SELECT id, full_name, active
      FROM intermediary
     WHERE upper(public.immutable_unaccent(full_name))
           LIKE upper(public.immutable_unaccent('%<επώνυμο>%'));
    ```

    Αν υπάρχει, σταματήστε: δεύτερος ίδιος διαμεσολαβητής δεν μπαίνει.

3.  Προσθέστε τον, με τη γραμμή του `audit_log`, σε μία συναλλαγή:

    ```sql
    BEGIN;

    WITH created AS (
        INSERT INTO intermediary (full_name, registry_number, phone, email)
        VALUES ('<Επώνυμο Όνομα>', NULL, NULL, NULL)
        RETURNING *
    )
    INSERT INTO audit_log (user_id, action, entity_type, entity_id, old_values, new_values)
    SELECT NULL, 'CREATE', 'Intermediary', id, NULL, to_jsonb(created)
      FROM created
    RETURNING entity_id, new_values;
    ```

    *   Το όνομα γράφεται όπως οι υπάρχοντες διαμεσολαβητές (ίδια σειρά
        επωνύμου και ονόματος, ίδια κεφαλαία ή πεζά), έως 200 χαρακτήρες.
    *   Αριθμός μητρώου έως 50 χαρακτήρες· τηλέφωνο 10 ψηφία· email.
    *   Ό,τι δεν ξέρετε μένει `NULL`, χωρίς εισαγωγικά.

4.  Διαβάστε ό,τι τύπωσε το `RETURNING`: ένα `entity_id`, και τα στοιχεία όπως
    τα θέλετε. Τότε:

    ```sql
    COMMIT;
    ```

    Αν κάτι είναι λάθος, γράψτε `ROLLBACK;` αντί για `COMMIT;`, και δεν
    γράφτηκε τίποτα. Ένα όνομα ακριβώς ίδιο με υπάρχον το αρνείται ο unique
    index `idx_intermediary_full_name`· και τότε `ROLLBACK;`.

5.  Ανοίξτε ένα συμβόλαιο στην εφαρμογή: ο νέος διαμεσολαβητής είναι στη
    λίστα «Διαμεσολαβών».
