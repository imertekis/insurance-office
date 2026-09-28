# Διαδικασίες του server (DEPLOYMENT)

Οι διαδικασίες της εφαρμογής στον server του γραφείου. Τις κάνετε εσείς,
στον server· το Claude Code δεν συνδέεται ποτέ εκεί (TASKS, Tasks 29–38,
Ε16). Τα Tasks 31–33 και 35 προσθέτουν εδώ την εγκατάσταση, τα αντίγραφα
ασφαλείας, την επαναφορά και τις ενημερώσεις· ως τότε, το αρχείο έχει μόνο
την παρακάτω διαδικασία.

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

1.  Ανοίξτε `psql` στη βάση, μέσα από το container της PostgreSQL, ώστε η
    ώρα της γραμμής να είναι ώρα Ελλάδας (Task 31):

    ```sh
    docker compose exec postgres psql -U <χρήστης της εφαρμογής> -d <βάση>
    ```

    Τα ονόματα είναι στο `.env`· το Task 31 γράφει εδώ την ακριβή εντολή.

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
