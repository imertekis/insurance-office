# DATA MODEL

PostgreSQL. Ονόματα πινάκων/στηλών στα αγγλικά (συμβατότητα με JPA
και εργαλεία), ετικέτες UI στα ελληνικά.

## Διάγραμμα σχέσεων

```
  intermediary
       │ 1
       │
       │ *
    policy *────1 vehicle 1────* ownership *────1 customer

  vehicle_brand: οι μάρκες που δέχεται το vehicle.brand (λίστα, όχι FK)
```

- Ένας πελάτης έχει πολλά οχήματα· ένα όχημα έχει πολλούς ιδιοκτήτες
  → `ownership` (join table με ποσοστό)
- Ένα όχημα έχει πολλά συμβόλαια στον χρόνο → `policy`
- Ένας διαμεσολαβών εξυπηρετεί πολλά συμβόλαια

---

## customer — ΠΕΛΑΤΗΣ

| Στήλη | Τύπος | Περιορισμοί | Excel |
|---|---|---|---|
| `id` | BIGSERIAL | PK | — |
| `tax_id` | VARCHAR(9) | NULL, UNIQUE, checksum | Α.Φ.Μ. |
| `entity_type` | VARCHAR(20) | NOT NULL, default `INDIVIDUAL` | — |
| `last_name` | VARCHAR(100) | NOT NULL | Επώνυμο |
| `first_name` | VARCHAR(100) | NULL για νομικά πρόσωπα | Όνομα |
| `father_name` | VARCHAR(100) | NULL | Πατρώνυμο |
| `birth_date` | DATE | NULL | Ημερομηνία Γέννησης |
| `license_date` | DATE | NULL | Ημ. Απόκτησης Διπλώματος |
| `tax_office` | VARCHAR(100) | NULL | Δ.Ο.Υ. |
| `street` | VARCHAR(200) | NULL | Οδός |
| `city` | VARCHAR(100) | NULL | Πόλη |
| `postal_code` | VARCHAR(5) | NULL | Τ.Κ. |
| `mobile` | VARCHAR(10) | NULL, αρχή `6` | Κινητό Τηλέφωνο |
| `phone` | VARCHAR(10) | NULL, αρχή `2` | Σταθερό Τηλέφωνο |
| `email` | VARCHAR(255) | NULL | Email |
| `search_normalized` | TEXT | generated, βλ. §Αναζήτηση | — |
| `name_sort` | TEXT | generated, collation `el-GR-x-icu` (Task 15) | — |
| `notes` | TEXT | NULL | — |
| `version` | BIGINT | optimistic locking | — |
| `created_at` / `updated_at` | TIMESTAMP | NOT NULL | — |

`entity_type`: `INDIVIDUAL` | `COMPANY`

`name_sort` = `last_name || coalesce(' ' || first_name, '')` με ICU collation
`el-GR-x-icu`, για τη λίστα πελατών και την αναζήτηση: ελληνική αλφαβητική σειρά, όπου οι τόνοι
και η διάκριση πεζών/κεφαλαίων δεν μετράνε, ώστε το «Άγγελος» να μπαίνει με το
Α. Δεν εξαρτάται από το collation της βάσης. Χρειάζεται PostgreSQL με ICU.

---

## vehicle — ΟΧΗΜΑ

| Στήλη | Τύπος | Περιορισμοί | Excel |
|---|---|---|---|
| `id` | BIGSERIAL | PK | — |
| `vin` | VARCHAR(17) | NOT NULL, UNIQUE | Αρ. Πλαισίου / VIN (E) |
| `plate` | VARCHAR(10) | NOT NULL | Αρ. Κυκλοφορίας (A) |
| `plate_normalized` | VARCHAR(10) | generated, UNIQUE | — |
| `brand` | VARCHAR(50) | NOT NULL | Μάρκα (D.1) |
| `model` | VARCHAR(100) | NOT NULL | Μοντέλο (D.3) |
| `first_registration` | DATE | NOT NULL | 1η Άδεια (B) |
| `license_issue_date` | DATE | NULL | Έκδοση (I) |
| `category` | VARCHAR(10) | NOT NULL | Κατηγορία (J) |
| `usage_type` | VARCHAR(20) | NOT NULL | Χρήση Οχήματος |
| `color` | VARCHAR(50) | NOT NULL | Χρώμα (R) |
| `seats` | SMALLINT | NULL | Θέσεις (S.1) |
| `engine_cc` | INTEGER | NULL (υποχρεωτικό στο app, εκτός αν fuel_type=ΗΛΕΚΤΡΙΣΜΟΣ) | Κυβικά (P.1) |
| `power_kw` | NUMERIC(6,2) | NOT NULL | Ισχύς kW (P.2) |
| `fuel_type` | VARCHAR(20) | NOT NULL | Καύσιμο (P.3) |
| `engine_number` | VARCHAR(50) | NULL | Αρ. Κινητήρα (P.5) |
| `co2` | INTEGER | NULL | CO2 (V.7) |
| `emission_standard` | VARCHAR(20) | NULL | Euro (V.9) |
| `weight_kg` | INTEGER | NULL | Βάρος kg (G) |
| `license_street` | VARCHAR(200) | NULL | Οδός (C.1.3) |
| `license_city` | VARCHAR(100) | NULL | Πόλη |
| `license_postal_code` | VARCHAR(5) | NULL | Τ.Κ. |
| `search_normalized` | TEXT | generated | — |
| `version`, `created_at`, `updated_at` | | | — |

`fuel_type`: `ΒΕΝΖΙΝΗ` | `ΠΕΤΡΕΛΑΙΟ` | `ΥΒΡΙΔΙΚΟ` | `ΗΛΕΚΤΡΙΣΜΟΣ` | `LPG` | `CNG`
`usage_type`: `ΕΙΧ` | `ΦΙΧ` | `ΔΧ` | `ΤΑΞΙ` | `ΛΕΩΦΟΡΕΙΟ`

> Τα `license_*` είναι **snapshot** της διεύθυνσης κατά την έκδοση
> της άδειας (πεδίο C.1.3). Δεν συγχρονίζονται με τη διεύθυνση του
> πελάτη — πρέπει να ταιριάζουν με το φυσικό έγγραφο.

> Στο δείγμα, το Tesla έχει `Κυβικά = 0`. Στη βάση αποθηκεύεται
> `NULL`: το μηδέν θα αλλοίωνε μέσους όρους και ταξινομήσεις.

### Τιμές από λίστα (Task 23a)

Μάρκα, κατηγορία, χρώμα και Euro παίρνουν τιμές μόνο από λίστα, γραμμένες
όπως τις γράφει η λίστα. Οι στήλες μένουν κείμενο, όχι foreign key.

| Πεδίο | Λίστα | Πού ζει |
|---|---|---|
| `brand` | οι μάρκες του πίνακα `vehicle_brand` (παρακάτω) | βάση (V7) |
| `category` | `M1` `M2` `M3` `N1` `N2` `N3` `O1` `O2` `O3` `O4` `L1e` `L2e` `L3e` `L4e` `L5e` `L6e` `L7e` `T` | κώδικας |
| `emission_standard` | `Euro 1` … `Euro 6`, `ZEV`· κενό επιτρέπεται | κώδικας |
| `color` | `Λευκό` `Μαύρο` `Γκρι` `Ασημί` `Μπλε` `Κόκκινο` `Πράσινο` `Κίτρινο` `Πορτοκαλί` `Καφέ` `Μπεζ` `Μπορντό` `Μωβ` `Ροζ` `Χρυσαφί`· δύο διαφορετικά ως `Λευκό-Μαύρο`· περισσότερα `Πολύχρωμο` | κώδικας |

«Κώδικας» είναι το `service/VehicleValues.java`: τις λίστες αυτές τις
ορίζουν κανονισμοί και η άδεια κυκλοφορίας, και μια νέα τιμή είναι νέα
έκδοση. Οι μάρκες αλλάζουν με νέο migration, μέχρι το Task 27.

- **Φόρμα:** μια νέα ή αλλαγμένη τιμή πρέπει να είναι της λίστας, όπως
  ακριβώς γράφεται εκεί. Μια παλιά τιμή εκτός λίστας (από πριν, ή από την
  εισαγωγή) σώζεται όσο μένει όπως είναι.
- **Θέσεις (`seats`):** 1 έως 99, σε κάθε κατηγορία.
- **Εισαγωγή Excel:** κάθε τιμή αντιστοιχίζεται στη λίστα, χωρίς
  πεζά/κεφαλαία και τόνους:
  - μάρκα: μέσω των συνωνύμων του `vehicle_brand` (`VW`, `V.W.` → `Volkswagen`)·
  - κατηγορία: και χωρίς κενά/παύλες, με τα ελληνικά γράμματα που μοιάζουν
    με λατινικά ως λατινικά, όπως οι πινακίδες (`Μ1` με ελληνικό Μ → `M1`)·
  - Euro: όπως η κατηγορία, και μια παραλλαγή με γράμμα στον αριθμό της
    (`Euro 6d-TEMP` → `Euro 6`, `Euro 5b` → `Euro 5`)·
  - χρώμα: μέσω των συνωνύμων `ΑΣΠΡΟ` → `Λευκό`, `ΑΣΗΜΕΝΙΟ` → `Ασημί`,
    `ΓΚΡΙΖΟ` → `Γκρι`, `ΚΑΦΕΤΙ` → `Καφέ`, `ΧΡΥΣΟ` → `Χρυσαφί`, `ΜΟΒ` → `Μωβ`·
    δύο χρώματα με παύλα ή κάθετο (`ΑΣΠΡΟ / ΜΑΥΡΟ` → `Λευκό-Μαύρο`)·
  - θέσεις 0 → `NULL`· αρνητικές ή πάνω από 99: η γραμμή απορρίπτεται.

  Ό,τι δεν αντιστοιχεί εισάγεται όπως είναι, και η αναφορά της εισαγωγής
  το γράφει, χωρίς να σταματά. Καμία εικασία: το `Ι.Χ.` στην κατηγορία
  δεν γίνεται ποτέ `M1` (μπορεί να είναι και `N1`), το `ΔΙΧΡΩΜΟ` και τρία
  χρώματα μένουν όπως είναι.
- **Υπάρχουσες εγγραφές:** το V8 εφάρμοσε τις ίδιες αντιστοιχίσεις στα
  οχήματα που υπήρχαν, με γραμμή `UPDATE` στο `audit_log` για καθένα που
  άλλαξε.
- **Αναζήτηση:** δεν ψάχνει στα συνώνυμα· μετά την αντιστοίχιση κάθε μάρκα
  γράφεται με έναν τρόπο.

---

## vehicle_brand — ΜΑΡΚΕΣ ΟΧΗΜΑΤΩΝ

| Στήλη | Τύπος | Περιορισμοί |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `name` | VARCHAR(50) | NOT NULL, UNIQUE χωρίς πεζά/κεφαλαία και τόνους |
| `synonyms` | TEXT[] | NOT NULL, default `{}` |

Οι μάρκες αυτοκινήτων, ελαφρών φορτηγών, τετράτροχων και μοτοσικλετών της
ελληνικής αγοράς, όπως γράφει την επωνυμία του ο κατασκευαστής
(`Mercedes-Benz`, `Citroën`, `Škoda`)· ο πλήρης κατάλογος είναι στο
`V7__vehicle_brand.sql`. Τα `synonyms` είναι γραφές που η εισαγωγή
αντιστοιχίζει στη μάρκα πέρα από πεζά/κεφαλαία και τόνους, μόνο όσες δεν
έχουν αμφιβολία: `Volkswagen` ← `VW`, `V.W.`· `Mercedes-Benz` ← `MERCEDES`,
`MERCEDES BENZ`. Μια γραφή που θα έδειχνε δύο μάρκες είναι λάθος του
migration: η ανάγνωση των μαρκών τότε αποτυγχάνει, και το
`VehicleServiceTest`, που διαβάζει τον πίνακα, το πιάνει πριν από την
έκδοση. Τον πίνακα τον γράφουν μόνο τα migrations· η εφαρμογή τον
διαβάζει.

---

## ownership — ΙΔΙΟΚΤΗΣΙΑ

| Στήλη | Τύπος | Περιορισμοί |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `vehicle_id` | BIGINT | FK → vehicle, NOT NULL |
| `customer_id` | BIGINT | FK → customer, NOT NULL |
| `percentage` | NUMERIC(5,2) | NOT NULL, 0 < p ≤ 100 |
| `is_primary` | BOOLEAN | NOT NULL |
| `from_date` | DATE | NULL |
| `to_date` | DATE | NULL = τρέχων ιδιοκτήτης |
| `created_at` | TIMESTAMP | NOT NULL |

- UNIQUE `(vehicle_id, customer_id, from_date)`
- Έλεγχος σε επίπεδο εφαρμογής: άθροισμα `percentage` ενεργών
  εγγραφών ανά όχημα = 100
- Ακριβώς μία εγγραφή με `is_primary = true` ανά όχημα

> Τα `from_date`/`to_date` επιτρέπουν ιστορικό μεταβιβάσεων χωρίς
> επιπλέον πίνακα. Στο v1 μπορούν να μένουν κενά. Η διόρθωση μιας
> αφαίρεσης (ο πελάτης ξαναμπαίνει με την ημερομηνία που έφυγε) ξανανοίγει
> την ίδια γραμμή και δεν γράφει νέα μεταβίβαση (Task 19).

---

## policy — ΣΥΜΒΟΛΑΙΟ

| Στήλη | Τύπος | Περιορισμοί | Excel |
|---|---|---|---|
| `id` | BIGSERIAL | PK | — |
| `policy_number` | VARCHAR(30) | NOT NULL, UNIQUE | Αρ. Συμβολαίου |
| `vehicle_id` | BIGINT | FK → vehicle, NOT NULL | — |
| `insurance_company` | VARCHAR(100) | NOT NULL | Ασφαλιστική Εταιρεία |
| `intermediary_id` | BIGINT | FK → intermediary | Διαμεσολαβούν Πρόσωπο |
| `start_date` | DATE | NOT NULL | Έναρξη Ασφάλειας |
| `end_date` | DATE | NOT NULL, > start_date | Λήξη Ασφάλειας |
| `premium` | NUMERIC(10,2) | NOT NULL | Πληρωτέα Μικτά Ασφάλιστρα |
| `surcharge` | BOOLEAN | NOT NULL, default false | Επασφάλιστρο |
| `surcharge_type` | VARCHAR(30) | NULL | Επασφάλιστρο (παρένθεση) |
| `version`, `created_at`, `updated_at` | | | — |

`surcharge_type`: `ΝΕΟΣ_ΟΔΗΓΟΣ` | `ΗΛΙΚΙΑΣ` | `ΑΛΛΟ`

- Συμβόλαια ίδιου οχήματος δεν επικαλύπτονται χρονικά
- Index στο `end_date` — τροφοδοτεί την οθόνη λήξεων
- «Τρέχον» = `CURRENT_DATE BETWEEN start_date AND end_date`

> Στο δείγμα υπάρχουν εξάμηνα (XYZ-1000, XYZ-2000) και ετήσια.
> Η διάρκεια δεν πρέπει να θεωρείται σταθερή πουθενά στον κώδικα.

---

## intermediary — ΔΙΑΜΕΣΟΛΑΒΩΝ

| Στήλη | Τύπος | Περιορισμοί |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `full_name` | VARCHAR(200) | NOT NULL, UNIQUE |
| `registry_number` | VARCHAR(50) | NULL |
| `phone` / `email` | VARCHAR | NULL |
| `active` | BOOLEAN | NOT NULL, default true |

---

## app_user / audit_log

`app_user`: `id`, `username` (UNIQUE), `password_hash`, `full_name`,
`role` (`ΥΠΑΛΛΗΛΟΣ` | `ΔΙΑΧΕΙΡΙΣΤΗΣ`), `active`, `last_login`

`audit_log`: `id`, `user_id`, `action` (`CREATE`/`UPDATE`/`DELETE`/`VIEW`),
`entity_type`, `entity_id`, `old_values` (JSONB), `new_values` (JSONB),
`ip_address`, `timestamp`

---

## Αναζήτηση

### Κανονικοποίηση κειμένου

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

`search_normalized` = `upper(unaccent(...))` των πεδίων αναζήτησης,
ενωμένων με κενό. Για `customer`: επώνυμο, όνομα, ΑΦΜ, κινητό,
σταθερό, email. Για `vehicle`: πινακίδα (κανονικοποιημένη), VIN,
μάρκα, μοντέλο.

### Κανονικοποίηση πινακίδας

Αφαίρεση παύλας/κενών, κεφαλαία, και **μετατροπή ελληνικών σε
λατινικά** για τους οπτικά όμοιους χαρακτήρες:

```
Α→A  Β→B  Ε→E  Ζ→Z  Η→H  Ι→I  Κ→K  Μ→M
Ν→N  Ο→O  Ρ→P  Τ→T  Υ→Y  Χ→X
```

Ώστε `ΑΒΕ-1234` και `ABE-1234` να δίνουν το ίδιο `plate_normalized`.
Το `plate` αποθηκεύεται **χωρίς παύλες και κενά** (`ΝΚΝ7777`), δεν διατηρείται η μορφή με παύλα (Task 14). Αποθηκεύεται επίσης **με κεφαλαία και χωρίς τόνους**, με το αλφάβητο όπως γράφτηκε: το «νκν-7777» σώζεται ως `ΝΚΝ7777`, το λατινικό «abe1234» ως `ABE1234`, λατινικό (Task 17, DECISIONS §5). Το `vin` αποθηκεύεται με κεφαλαία.

### Indexes

```sql
CREATE INDEX idx_customer_search ON customer
  USING gin (search_normalized gin_trgm_ops);
CREATE INDEX idx_vehicle_search ON vehicle
  USING gin (search_normalized gin_trgm_ops);
CREATE UNIQUE INDEX idx_customer_tax_id ON customer (tax_id);
CREATE UNIQUE INDEX idx_vehicle_vin ON vehicle (vin);
CREATE UNIQUE INDEX idx_vehicle_plate ON vehicle (plate_normalized);
CREATE INDEX idx_policy_end_date ON policy (end_date);
CREATE INDEX idx_ownership_vehicle ON ownership (vehicle_id);
CREATE INDEX idx_ownership_customer ON ownership (customer_id);
```

---

## Έλεγχος ΑΦΜ

9 ψηφία. Τα πρώτα 8 με βάρη 2⁸…2¹, άθροισμα `mod 11 mod 10` =
9ο ψηφίο.

```java
public static boolean isValidTaxId(String afm) {
    if (afm == null || !afm.matches("\\d{9}")) return false;
    int sum = 0;
    for (int i = 0; i < 8; i++)
        sum += (afm.charAt(i) - '0') << (8 - i);
    return sum % 11 % 10 == afm.charAt(8) - '0';
}
```

Και τα 8 ΑΦΜ του δείγματος περνούν αυτόν τον έλεγχο.

---

## Αναμενόμενο αποτέλεσμα import

| Πίνακας | Εγγραφές |
|---|---|
| `intermediary` | 1 |
| `customer` | 8 (μία εγγραφή, π.χ. Αλεξίου Μαρία, έχει μόνο όνομα και ΑΦΜ) |
| `vehicle` | 8 |
| `ownership` | 9 (7×100% + 2×50%) |
| `policy` | 8 |
