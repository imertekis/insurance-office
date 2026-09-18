# Code Review: Tasks 8, 9 & 10

## 1. Spring Security Configuration
- **Session Handling:** The configuration relies on Spring Boot defaults (a session is created if required). However, no explicit session timeout (`server.servlet.session.timeout`) or "Remember Me" functionality is configured. The SPEC explicitly mentions "A clerk should not have to log in repeatedly during the day", so the default 30-minute idle timeout might conflict with this business requirement.
- **CSRF:** Cross-Site Request Forgery (CSRF) protection is left enabled (default in Spring Security). This is the correct approach for Thymeleaf applications, and forms will automatically include the CSRF token.
- **Public Endpoints:** The configuration appropriately permits `/webjars/**`, `/login`, and `/?logout` globally. However, if standard static assets (`/css/**`, `/js/**`, `/images/**`) are ever added to the project outside of WebJars, they will unexpectedly be blocked by the `.anyRequest().authenticated()` rule.
- **Password Encoding:** `BCryptPasswordEncoder` is correctly used, ensuring industry-standard security for storing credentials.

## 2. XSS Exposure in Thymeleaf Templates
- **No XSS Vulnerabilities Found:** User-supplied text is correctly and safely rendered throughout the views.
- **Safe Rendering Attributes:** Across `customer-detail.html`, `vehicle-detail.html`, `search-results.html`, and `dashboard.html`, text output exclusively uses `th:text` (which safely HTML-escapes content).
- **Search Query Handling:** In `fragments/header.html`, the potentially malicious search query string is safely bound using `th:value="${searchQuery}"`, ensuring it is securely escaped before being rendered inside the HTML input attribute.
- **No Unescaped Content:** There are no unsafe `th:utext` attributes or raw inline expressions (like `[[${...}]]`) within `<script>` tags or HTML bodies.

## 3. Data Exposure and Controller Leaks
- **No Sensitive Data Leaks:** The controllers and Data Transfer Objects (DTOs) strictly return business domain data (e.g., `CustomerDto`, `PolicyViewDto`). Sensitive fields, such as the `passwordHash` loaded in `AppUserDetails`, do not leak into the JSON API or view context.
- **Role Validation Gap:** While `AppUserDetails` successfully captures the `ROLE_ΥΠΑΛΛΗΛΟΣ` and `ROLE_ΔΙΑΧΕΙΡΙΣΤΗΣ` authorities, there are currently no authorization checks (`@PreAuthorize` or `requestMatchers`) restricting endpoint access. Presently, all authenticated users have identical permissions. While the `ΔΙΑΧΕΙΡΙΣΤΗΣ`-specific endpoints (user management, audit logs, deletions) have not been implemented yet, they will need explicit role-based access control once introduced.

## 4. N+1 and Unbounded Queries in Pages
- **Unbounded Queries in Dashboard:**
  - `DashboardService.expiries()` executes an unbounded query via `PolicyRepository.findNotRenewedEndingBetween()`. Without any `LIMIT` or pagination logic, an excessive number of policies expiring within the 90-day window will fetch thousands of rows into memory, which could crash the JVM or cause severe latency.
  - `PolicyRepository.findInsuranceCompanies()` is similarly unbounded, retrieving all distinct companies by scanning the entire `Policy` table.
- **Unbounded Queries in Card Pages:**
  - `CustomerService.findDetail()` and `VehicleService.findDetail()` unconditionally load the complete historical lists of `Ownership` and `Policy` records. For a commercial customer with an extensive fleet over multiple years, these unbounded collections could cause massive payload sizes and memory pressure.
- **N+1 Query Prevention:**
  - In `VehicleService.findDetail()`, the mapping logic (`policyViews()`) accesses `policy.vehicle.plate`. Although `findByVehicleIdWithIntermediary()` avoids eagerly fetching the `Vehicle`, no N+1 query is triggered. This is because `VehicleService` primes the L1 cache via `vehicleRepository.findById(id)` immediately before fetching the policies, allowing Hibernate to efficiently resolve the lazy `Vehicle` references from the persistent context.
