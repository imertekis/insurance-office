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
*   `V2__unaccent_wrapper.sql` (Extensions `unaccent`/`pg_trgm`, custom `IMMUTABLE` PostgreSQL συνάρτηση γύρω από την `unaccent`, στήλη `customer.search_normalized` ως `GENERATED ALWAYS AS (...) STORED` και GIN index πάνω της)
*   `TextNormalizationUtils.java` (Java utility class: αφαίρεση τόνων/κεφαλαία για το input αναζήτησης, κανονικοποίηση πινακίδας με αντιστοίχιση ελληνικών→λατινικών)
*   `TextNormalizationUtilsTest.java` (Unit tests)
*   `Customer.java` (Αντιστοίχιση του `search_normalized` ως read-only: `insertable = false, updatable = false`. **Όχι** `@PrePersist` για αυτό το πεδίο.)
*   `SearchNormalizationConsistencyTest.java` (Integration test με Testcontainers)
**Απόφαση:** Το `search_normalized` υπολογίζεται στη **βάση** (generated column), ώστε να γεμίζει και σε εγγραφές που παρακάμπτουν το JPA (import, χειροκίνητο SQL, migrations). Το `plate_normalized` μένει σε `@PrePersist`/`@PreUpdate` στο `Vehicle` (Task 3) μέσω του `TextNormalizationUtils`, γιατί η αντιστοίχιση ελληνικών→λατινικών είναι δύσχρηστη σε SQL.
**Κριτήρια Αποδοχής:** Το Flyway migration περνάει. Τα unit tests αποδεικνύουν ότι τα τονισμένα ελληνικά γίνονται άτονα, οι ελληνικοί χαρακτήρες πινακίδων (π.χ. 'Α', 'Β', 'Ε') γίνονται οι λατινικοί 'A', 'B', 'E', οι παύλες/κενά αφαιρούνται από τις πινακίδες και όλα μετατρέπονται σε κεφαλαία. Το `search_normalized` γεμίζει αυτόματα τόσο κατά την αποθήκευση Customer μέσω JPA όσο και με απευθείας `INSERT` σε SQL. Το consistency test επιβεβαιώνει ότι η Java κανονικοποίηση του input δίνει το ίδιο αποτέλεσμα με τη συνάρτηση της βάσης (τονισμένα, πεζά/κεφαλαία, τελικό σίγμα).

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
**Σημείωση:** Ο importer είναι εργαλείο **εφάπαξ μετάπτωσης** από τα Excel, όχι λειτουργία καθημερινής χρήσης. Οι κίνδυνοι στο `docs/NOTES.md` («Import risks»: η επανεκτέλεση αντικαθιστά αλλαγές που έγιναν στην εφαρμογή, και αφαιρεί ιδιοκτησίες που λείπουν από το αρχείο) γίνονται πραγματικοί μόλις παραδοθεί το Task 11, γιατί από εκεί και πέρα τα δεδομένα αλλάζουν μέσα από την εφαρμογή.

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
*   Αφαίρεση του προσωρινού REST endpoint `GET /api/customers` του Task 1 (αντικαθίσταται από Thymeleaf) και ενημέρωση του αντίστοιχου test
**Κριτήρια Αποδοχής:** Η εφαρμογή επιστρέφει HTML. Η αρχική οθόνη (root `/`) τραβάει από το `PolicyRepository` και προβάλλει εξ ορισμού τα συμβόλαια που λήγουν σε 30 ημέρες σε μορφή πίνακα, ταξινομημένα κατά ημερομηνία λήξης. Κάθε γραμμή: πινακίδα, πελάτης, κινητό, ημερομηνία λήξης, ασφαλιστική, ασφάλιστρο. Φίλτρα (SPEC §7.1): 7 / 30 / 60 / 90 ημέρες, ανά ασφαλιστική, και ήδη ληγμένα.

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

## Task 11: Φόρμες Καταχώρησης (Δημιουργία & Επεξεργασία)
**Στόχος:** Δημιουργία και επεξεργασία Πελάτη, Οχήματος, Ιδιοκτησίας και Συμβολαίου μέσω φορμών Thymeleaf. Το Task 9 καλύπτει μόνο προβολή και πλοήγηση, ενώ το γραφείο καταχωρεί νέες εγγραφές καθημερινά.
**Αρχεία (δημιουργία/τροποποίηση):**
*   `templates/customer-form.html`, `templates/vehicle-form.html`, `templates/ownership-form.html`, `templates/policy-form.html`
*   Αντίστοιχοι Spring MVC Controllers (φόρμα και αποθήκευση, μόνο με DTOs)
*   Services για δημιουργία/ενημέρωση (`CustomerService`, `VehicleService`, `OwnershipService`, `PolicyService`)
*   Integration tests για κάθε φόρμα
**Κριτήρια Αποδοχής:** Ο υπάλληλος δημιουργεί και επεξεργάζεται Πελάτη, Όχημα, Ιδιοκτησία και Συμβόλαιο από τις καρτέλες του Task 9. Οι φόρμες χρησιμοποιούν την επικύρωση των services, χωρίς να την επαναλαμβάνουν στον controller ή σε JavaScript. Οι κανόνες του Πελάτη υπάρχουν ήδη στο `CustomerService` (Task 7)· οι κανόνες οχήματος και συμβολαίου προστίθενται σε αυτό το Task στα `VehicleService` και `PolicyService`:
*   checksum ΑΦΜ (DATA_MODEL «Έλεγχος ΑΦΜ»)· ΑΦΜ που λείπει δίνει προειδοποίηση και δεν μπλοκάρει την αποθήκευση (Task 7)
*   μορφή VIN `^[A-HJ-NPR-Z0-9]{17}$`
*   άθροισμα ποσοστών ιδιοκτησίας ανά όχημα = 100 και ακριβώς ένας κύριος ιδιοκτήτης (`OwnershipService`). Η φόρμα ιδιοκτησίας επεξεργάζεται μαζί όλους τους τρέχοντες ιδιοκτήτες του οχήματος, αφού μία-μία γραμμή δεν μπορεί να περάσει τον έλεγχο
*   συμβόλαια του ίδιου οχήματος που δεν επικαλύπτονται χρονικά

Κάθε σφάλμα εμφανίζεται μέσα στη φόρμα, δίπλα στο πεδίο που αφορά (ή στην κορυφή, αν αφορά όλη την εγγραφή), και οι τιμές που πληκτρολογήθηκαν δεν χάνονται. Σύγκρουση optimistic locking (Task 7) εμφανίζει μήνυμα στη φόρμα και δεν αντικαθιστά σιωπηλά την αλλαγή του άλλου χρήστη.

## Task 12: Ροή Ανανέωσης Συμβολαίου
**Στόχος:** Ενέργεια «Ανανέωση» από την αρχική οθόνη λήξεων (Task 8) και από την καρτέλα οχήματος (Task 9), που προσυμπληρώνει νέο συμβόλαιο από το τρέχον. Είναι η συχνότερη καθημερινή ενέργεια του γραφείου και δεν πρέπει να απαιτεί συμπλήρωση κενής φόρμας.
**Αρχεία (δημιουργία/τροποποίηση):**
*   `PolicyService.java` (προσυμπλήρωση νέου συμβολαίου από το τρέχον)
*   Controller action ανανέωσης
*   `templates/policy-form.html` (η φόρμα του Task 11, όχι νέα)
*   `templates/dashboard.html`, `templates/vehicle-detail.html` (κουμπί «Ανανέωση»)
*   Integration tests
**Κριτήρια Αποδοχής:** Το κουμπί «Ανανέωση» υπάρχει σε κάθε γραμμή της αρχικής οθόνης και στο τρέχον συμβόλαιο της καρτέλας οχήματος. Ανοίγει τη φόρμα συμβολαίου του Task 11 προσυμπληρωμένη από το τρέχον συμβόλαιο (όχημα, ασφαλιστική εταιρεία, διαμεσολαβών, επασφάλιστρο και τύπος, ασφάλιστρο). Ο υπάλληλος αλλάζει ημερομηνίες, αριθμό συμβολαίου και ασφάλιστρο. Η αποθήκευση δημιουργεί **νέο** συμβόλαιο· το προηγούμενο μένει αμετάβλητο ως ιστορικό. Ισχύει όλη η επικύρωση του Task 11 (μοναδικός αριθμός συμβολαίου, λήξη > έναρξη, μη επικάλυψη). Η διάρκεια δεν υποτίθεται: η νέα λήξη δεν υπολογίζεται αυτόματα ως +6 ή +12 μήνες.
**Ανοιχτό ερώτημα (πριν την υλοποίηση):** Προσυμπληρώνεται η νέα έναρξη με τη λήξη του τρέχοντος; Στα δεδομένα η λήξη ενός συμβολαίου είναι ίδια μέρα με την έναρξη του επόμενου (π.χ. 01/03/2026 → 01/03/2027), οπότε πρέπει πρώτα να οριστεί αν αυτό μετράει ως επικάλυψη.
