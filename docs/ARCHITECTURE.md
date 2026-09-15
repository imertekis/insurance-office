# Αρχιτεκτονική Εφαρμογής (ARCHITECTURE)

Το παρόν έγγραφο περιγράφει την αρχιτεκτονική της εφαρμογής (Java/Spring Boot), την οργάνωση του κώδικα και τον τρόπο υλοποίησης κεντρικών μηχανισμών.

## 1. Επιλογή Frontend

Για μια εσωτερική εφαρμογή 3 χρηστών στο ίδιο γραφείο, επιλέγεται η λύση **Server-rendered HTML με Thymeleaf (και λίγο Vanilla JS/HTMX)**.
*   **Αιτιολόγηση:** Εξαλείφει την πολυπλοκότητα ενός ξεχωριστού frontend project (π.χ. React/Angular), αποφεύγει τη χρήση Node.js στο build και είναι ιδανικό/γρηγορότερο για έναν junior developer που δουλεύει μόνος του.

## 2. Στρατηγική Database Migrations

Για τη διαχείριση του σχήματος της βάσης δεδομένων επιλέγεται το **Flyway**.
*   **Αιτιολόγηση:** Λειτουργεί με καθαρά SQL scripts, κάνοντας την υλοποίηση πολύπλοκων custom indexes (`pg_trgm`) και PostgreSQL functions πολύ πιο άμεση και κατανοητή από το YAML/XML format του Liquibase.

## 3. Δομή Πακέτων (Package Structure)

Το βασικό πακέτο της εφαρμογής είναι το `gr.insuranceoffice`. Ακολουθείται αυστηρό technical layering:

*   `gr.insuranceoffice.controller`: Οι Spring MVC Controllers που δέχονται HTTP requests, καλούν τα services και επιστρέφουν Thymeleaf templates.
*   `gr.insuranceoffice.service`: Το business logic και η διαχείριση των transactions (`@Transactional`).
*   `gr.insuranceoffice.repository`: Τα Spring Data JPA interfaces.
*   `gr.insuranceoffice.entity`: Αποκλειστικά τα JPA Entities.
*   `gr.insuranceoffice.dto`: Τα Data Transfer Objects για μεταφορά δεδομένων από/προς το UI.
*   `gr.insuranceoffice.mapper`: Κλάσεις που χρησιμοποιούν το **MapStruct** για τη μετατροπή μεταξύ Entity και DTO.
    *   **Αιτιολόγηση:** Προσφέρει type-safe mappers (χωρίς reflection) με αυτόματη παραγωγή κώδικα στο compilation.
*   `gr.insuranceoffice.config`: Οι ρυθμίσεις της εφαρμογής (Security, JPA, Web).
*   `gr.insuranceoffice.security`: Η λογική ταυτοποίησης (Session-based auth) και το authorization του Spring Security.
*   `gr.insuranceoffice.importer`: Εξειδικευμένο πακέτο που περιέχει τη λογική ανάγνωσης (π.χ. μέσω Apache POI) και εισαγωγής των αρχείων Excel.
    *   **Αιτιολόγηση:** Απομονώνει τον batch κώδικα μετασχηματισμού των αρχείων Excel από το βασικό lifecycle της εφαρμογής.

## 4. Λογική Αναζήτησης (Type Detection + Query Building)

Η άμεση αναζήτηση υλοποιείται εντός του `gr.insuranceoffice.service.SearchService`.

1.  **Type Detection**: Το `SearchService` αναλύει την είσοδο του χρήστη αποκλειστικά μέσω Regular Expressions (π.χ. `^.{17}$` = VIN, `^[0-9]{9}$` = ΑΦΜ).
2.  **Query Building**: Ανάλογα με τον τύπο, καλεί την κατάλληλη μέθοδο:
    *   ΑΦΜ: `CustomerRepository.findByTaxId()`.
    *   VIN: `VehicleRepository.findByVin()`.
    *   Ελεύθερο/Απροσδιόριστο: Καλεί τις μεθόδους `search()` σε `CustomerRepository` και `VehicleRepository` αξιοποιώντας τον GIN index.

## 5. Κανονικοποίηση (Normalization)

1.  **Κατά την Αποθήκευση (Write Path)**:
    *   **Πινακίδες (`plate_normalized`)**: Υλοποιείται στο **Entity Layer** μέσω JPA Callbacks (`@PrePersist`, `@PreUpdate`).
        *   **Αιτιολόγηση:** Εξασφαλίζει πως κανένα service δεν θα "ξεχάσει" να κανονικοποιήσει την πινακίδα (κεφαλαία, χωρίς παύλες, ελληνικά σε λατινικά).
    *   **Αναζήτηση (`search_normalized`)**: Επειδή η ενσωματωμένη συνάρτηση `unaccent()` της PostgreSQL δεν είναι `IMMUTABLE` (και άρα απαγορεύεται σε generated columns/indexes), θα γραφτεί μια **custom IMMUTABLE wrapper function** γύρω από την `unaccent()` μέσω ενός Flyway script.
        *   **Αιτιολόγηση:** Διατηρεί την ευθύνη του συνδυασμού και της αφαίρεσης τόνων πλήρως στη βάση, απαλλάσσοντας την Java από σύνθετη λογική string concatenation.
2.  **Κατά την Αναζήτηση (Read Path)**:
    *   Πριν το `SearchService` στείλει το query, περνάει το string του χρήστη από την ίδια λογική κανονικοποίησης μέσω ενός Java utility (αφαίρεση τόνων/κεφαλαία) για να γίνει σωστά το match στο `search_normalized` ή `plate_normalized`.

## 6. Optimistic Locking και Audit Logging

### Optimistic Locking
*   **Πού ζει**: Στο `gr.insuranceoffice.entity`.
*   **Πώς λειτουργεί**: Στα Entities προστίθεται ένα πεδίο τύπου `Long` με το `@Version`. Αν υπάρξει conflict κατά το save, ρίχνεται `OptimisticLockException` και το Spring MVC Controller επιστρέφει το αντίστοιχο σφάλμα (409 Conflict) στο UI.

### Audit Logging
Με δεδομένο ότι το μοντέλο (`DATA_MODEL.md`) απαιτεί ένα custom schema (`audit_log` με `old_values` / `new_values` σε `JSONB`), η λύση του Hibernate Envers **απορρίπτεται**.
*   **Αιτιολόγηση:** Το Envers δημιουργεί το δικό του σχήμα με πολλαπλούς `_AUD` πίνακες και δεν χρησιμοποιεί JSONB format για καταγραφή αλλαγών.
*   **Υλοποίηση**: Το audit logging θα στηθεί μέσω ενός **custom JPA `@EntityListener`**. Ο listener αυτός:
    1.  Θα intercept-άρει τις `@PreUpdate`, `@PostPersist` και `@PreRemove` ενέργειες.
    2.  Θα παίρνει το user ID από το `SecurityContextHolder`.
    3.  Θα μετατρέπει το entity state σε JSON (μέσω Jackson).
    4.  Θα γράφει τα δεδομένα απευθείας στον πίνακα `audit_log`, ως μέρος του ίδιου transaction.
