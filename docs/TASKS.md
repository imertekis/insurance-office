# Σχέδιο Υλοποίησης (TASKS)

Το παρακάτω σχέδιο χωρίζει την υλοποίηση της εφαρμογής σε λογικά, διαδοχικά βήματα. Τα πιο επίφοβα (risky) κομμάτια, όπως η κανονικοποίηση κειμένου, το "έξυπνο" search και η εισαγωγή των Excel, τοποθετούνται πολύ νωρίς, ώστε τυχόν προβλήματα να εντοπιστούν πριν χτιστεί το UI και οι υπόλοιπες λειτουργίες πάνω τους.

Κάθε Task είναι σχεδιασμένο να υλοποιείται σε ένα session και να αφήνει την εφαρμογή λειτουργική, με τα tests της να περνούν επιτυχώς.

---

## Task 1: Project Skeleton & End-to-End Slice
**Στόχος:** Στήσιμο του περιβάλλοντος και δημιουργία του πρώτου λειτουργικού "end-to-end slice" της εφαρμογής (βάση, backend, api) με μόνο έναν πίνακα, ως απόδειξη ότι η υποδομή λειτουργεί.
**Αρχεία (δημιουργία/τροποποίηση):**
*   `docker-compose.yml` (PostgreSQL)
*   `pom.xml` (Spring Boot Web, Data JPA, Flyway, PostgreSQL Driver, Testcontainers)
*   `application.yml` (DB connection, Flyway config)
*   `src/main/resources/db/migration/V1__init_customer.sql` (Δημιουργία πίνακα `customer` μόνο)
*   `Customer.java` (JPA Entity)
*   `CustomerRepository.java` (Spring Data Repo)
*   `CustomerController.java` (REST endpoint: `GET /api/customers`)
*   `CustomerControllerTest.java` (Integration test)
**Κριτήρια Αποδοχής:** Η εντολή `docker-compose up` ξεκινάει τη βάση. Το Spring Boot ξεκινάει χωρίς λάθη και τρέχει το Flyway migration. Μια κλήση στο `/api/customers` επιστρέφει JSON 200 OK. Το test περνάει επιτυχώς.

## Task 2: Search Normalization Foundation (High Risk)
**Στόχος:** Υλοποίηση της κανονικοποίησης κειμένου. Αυτό πρέπει να γίνει νωρίς, ώστε να υπάρχει ένα και μοναδικό σημείο αλήθειας (Single Source of Truth) τόσο για το Entity Lifecycle όσο και για το SearchService.
**Αρχεία (δημιουργία/τροποποίηση):**
*   `V2__unaccent_wrapper.sql` (Δημιουργία custom `IMMUTABLE` PostgreSQL συνάρτησης γύρω από την `unaccent`)
*   `TextNormalizationUtils.java` (Java utility class)
*   `TextNormalizationUtilsTest.java` (Unit tests)
*   `Customer.java` (Προσθήκη `@PrePersist`/`@PreUpdate` για `search_normalized`)
**Κριτήρια Αποδοχής:** Το Flyway migration περνάει. Τα unit tests αποδεικνύουν ότι τα τονισμένα ελληνικά γίνονται άτονα, οι ελληνικοί χαρακτήρες πινακίδων (π.χ. 'Α', 'Β', 'Ε') γίνονται οι λατινικοί 'A', 'B', 'E', και όλα μετατρέπονται σε κεφαλαία. Κατά την αποθήκευση Customer, το `search_normalized` γεμίζει αυτόματα.

## Task 3: Πλήρες Σχήμα & MapStruct DTOs
**Στόχος:** Ολοκλήρωση του Data Model (χωρίς soft deletes) βάσει των προδιαγραφών και εισαγωγή των DTOs για την απομόνωση του domain model.
**Αρχεία (δημιουργία/τροποποίηση):**
*   `V3__full_schema.sql` (Πίνακες `vehicle`, `policy`, `ownership`, `intermediary`, `app_user`, `audit_log` και GIN indexes)
*   Entities (`Vehicle.java`, `Policy.java` κλπ.)
*   Repositories για τα entities.
*   `pom.xml` (Προσθήκη MapStruct)
*   `*Dto.java` και `*Mapper.java` (MapStruct interfaces)
**Κριτήρια Αποδοχής:** Το Spring Data JPA ξεκινάει χωρίς errors για τις σχέσεις (OneToMany, ManyToMany). Το πεδίο `engine_cc` είναι nullable. Τα πεδία `tax_id` και `mobile` είναι nullable. Τα unique indexes υπάρχουν και δεν έχουν `WHERE deleted_at IS NULL`. Οι MapStruct mappers γίνονται generate στο compile-time επιτυχώς.

## Task 4: Excel Import Service (High Risk)
**Στόχος:** Ανάγνωση των παλιών Excel αρχείων και μεταφορά τους στη νέα βάση δεδομένων, ώστε να έχουμε ρεαλιστικά δεδομένα για το υπόλοιπο development.
**Αρχεία (δημιουργία/τροποποίηση):**
*   `pom.xml` (Προσθήκη Apache POI)
*   `gr.insuranceoffice.importer.ExcelImporterService.java`
*   `ExcelImporterServiceTest.java`
**Κριτήρια Αποδοχής:** Η υπηρεσία διαβάζει ένα dummy Excel και το χαρτογραφεί στα entities. Μετατρέπει τα strings `«ΝΑΙ (Ν.Ο.Δ.)»` σε boolean. Αν το service τρέξει 2 φορές στο ίδιο αρχείο (idempotency), δεν δημιουργεί διπλότυπα αλλά κάνει update μέσω του natural key (`tax_id`, `vin`). Η εγγραφή "Αλεξίου Μαρία" σώζεται κανονικά παρόλο που λείπουν τα τηλέφωνα. 

## Task 5: Έξυπνη Αναζήτηση (Smart Search)
**Στόχος:** Υλοποίηση της single search box λογικής με Type Detection μέσω Regex.
**Αρχεία (δημιουργία/τροποποίηση):**
*   `SearchService.java`
*   `SearchServiceTest.java`
*   `SearchResultDto.java`
*   `SearchController.java`
**Κριτήρια Αποδοχής:** Το service δέχεται ένα input string. Αν κάνει match το VIN regex (`^[A-HJ-NPR-Z0-9]{17}$`), ψάχνει μόνο στο `VehicleRepository.findByVin()`. Αν είναι 9 ψηφία, στο ΑΦΜ. Αλλιώς, περνάει το input από το `TextNormalizationUtils`, εκτελεί fallback GIN query στους πίνακες `customer` και `vehicle`, συλλέγει τα αποτελέσματα και τα ομαδοποιεί. Τα tests επικυρώνουν σωστό routing του regex και απουσία N+1 queries.

## Task 6: Audit Logging Entity Listener
**Στόχος:** Αυτοματοποίηση της καταγραφής αλλαγών (GDPR) με JSONB diffs, αποφεύγοντας το Hibernate Envers.
**Αρχεία (δημιουργία/τροποποίηση):**
*   `AuditListener.java`
*   Προσθήκη `@EntityListeners(AuditListener.class)` στα Entities.
**Κριτήρια Αποδοχής:** Κάθε Insert, Update ή Hard Delete (`repository.delete()`) σε Customer ή Vehicle, παράγει εντός του ίδιου transaction μια εγγραφή στον πίνακα `audit_log` (με `old_values` και `new_values` σε JSON μορφή).

## Task 7: Optimistic Locking & Advanced Validation
**Στόχος:** Εφαρμογή των επιχειρησιακών κανόνων και του concurrency control.
**Αρχεία (δημιουργία/τροποποίηση):**
*   Προσθήκη `@Version` πεδίου `version` σε Πελάτη, Όχημα, Συμβόλαιο.
*   `CustomerService.java` (Business validations)
*   `GlobalExceptionHandler.java` (Controller Advice)
**Κριτήρια Αποδοχής:** Μια ταυτόχρονη αλλαγή ρίχνει `OptimisticLockException` και το API επιστρέφει HTTP 409 Conflict. Η αποθήκευση Πελάτη **χωρίς κινητό** περνάει, ΕΚΤΟΣ αν ο πελάτης είναι "Κύριος Ιδιοκτήτης" (is_primary = true) σε ενεργό όχημα με τρέχον συμβόλαιο, οπότε και το service ρίχνει Business Exception. Απουσία ΑΦΜ δεν ρίχνει λάθος αλλά μπορεί να επιστρέψει προειδοποίηση.

## Task 8: UI - Thymeleaf Views & Dashboard
**Στόχος:** Μετάβαση από REST JSON σε Server-rendered HTML και δημιουργία της Αρχικής Οθόνης (Λήξεις).
**Αρχεία (δημιουργία/τροποποίηση):**
*   `pom.xml` (Thymeleaf, Webjars για CSS/Bootstrap)
*   `templates/layout.html`, `templates/dashboard.html`
*   `DashboardController.java`
**Κριτήρια Αποδοχής:** Η εφαρμογή επιστρέφει HTML. Η αρχική οθόνη (root `/`) τραβάει από το `PolicyRepository.findByEndDateBetween` και προβάλλει τα συμβόλαια που λήγουν σε 30 ημέρες σε μορφή πίνακα, με φίλτρα 7 / 30 / 60 ημερών.

## Task 9: UI - Search & Καρτέλες (CRUD Views)
**Στόχος:** Ολοκλήρωση του Frontend για προβολή και αναζήτηση δεδομένων (Αμφίδρομη πλοήγηση).
**Αρχεία (δημιουργία/τροποποίηση):**
*   `templates/fragments/header.html` (Single Search Box)
*   `templates/search-results.html`
*   `templates/customer-detail.html`
*   `templates/vehicle-detail.html`
*   Αντίστοιχοι Spring MVC Controllers
**Κριτήρια Αποδοχής:** Ο χρήστης πληκτρολογεί στο header. Πατώντας Enter, τα αποτελέσματα εμφανίζονται με ετικέτα τύπου. Κάνοντας κλικ, μπαίνει στην Καρτέλα του Οχήματος (που εμφανίζει τεχνικά χαρακτηριστικά, συμβόλαια και ιδιοκτήτες) ή του Πελάτη. Όλα τα ονόματα και οι πινακίδες είναι HTML links (αμφίδρομη πλοήγηση).

## Task 10: Security (Authentication)
**Στόχος:** Κλείδωμα της εφαρμογής με login.
**Αρχεία (δημιουργία/τροποποίηση):**
*   `pom.xml` (Spring Security)
*   `SecurityConfig.java`
*   `CustomUserDetailsService.java`
*   `templates/login.html`
**Κριτήρια Αποδοχής:** Οποιοδήποτε URL ανακατευθύνει στο `/login`. Η σύνδεση επαληθεύεται με κωδικούς (bcrypt) από τον πίνακα `app_user`. Μετά το login, ο `SecurityContextHolder` τροφοδοτεί σωστά το username στον `AuditListener` του Task 6.
