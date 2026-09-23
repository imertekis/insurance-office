# Review: form binding (Tasks 11a–11e) and delete-path guards

Scope: for each Task 11a–11e form controller, which fields a POST binds
from the request body, and whether `id`, `version`, or (for policies) the
owning vehicle id could be set or changed by adding extra parameters to
the request. Then, every code path that deletes a customer, vehicle,
policy or ownership, and whether it runs through a
`@PreAuthorize`-guarded service method. No code changed.

## 1. Customer form — `CustomerController` (Task 11a)

`create`/`update` bind with `@ModelAttribute("customer") CustomerDto`,
i.e. Spring binds every request parameter whose name matches a
`CustomerDto` component: `id`, `taxId`, `entityType`, `lastName`,
`firstName`, `fatherName`, `birthDate`, `licenseDate`, `taxOffice`,
`street`, `city`, `postalCode`, `mobile`, `phone`, `email`, `notes`,
`version`.

- **`id`**: bindable on the DTO, but never read on the write path.
  `create` always does `new Customer()`. `update` looks the entity up by
  the `{id}` **path variable**, not `dto.id()`. `CustomerMapper.updateEntity`
  copies onto the entity via setters, and `Customer` has no `setId`
  (confirmed: no `setId` in `entity/Customer.java`), so MapStruct silently
  skips it. **Not settable.**
- **`version`**: bindable, and *is* read — but only for the optimistic-lock
  comparison (`CustomerService.update`, `dto.version() != customer.getVersion()`
  → `ObjectOptimisticLockingFailureException`). `Customer` has no `setVersion`,
  so it can't be written onto the entity either way. **Read-only use, not
  settable.**
- No vehicle id on this form (customers don't reference a vehicle).

## 2. Vehicle form — `VehicleController` (Task 11b)

`create`/`update` bind with `@ModelAttribute("vehicle") VehicleDto`:
`id`, `vin`, `plate`, `brand`, `model`, `firstRegistration`,
`licenseIssueDate`, `category`, `usageType`, `color`, `seats`,
`engineCc`, `powerKw`, `fuelType`, `engineNumber`, `co2`,
`emissionStandard`, `weightKg`, `licenseStreet`, `licenseCity`,
`licensePostalCode`, `version`.

- **`id`**: bindable, but same pattern as Customer — `update` uses the
  `{id}` path variable for `findById`, not `dto.id()`; `Vehicle` has no
  `setId`. **Not settable.**
- **`version`**: bindable, used only for the optimistic-lock check; no
  `setVersion` on `Vehicle`. **Read-only use, not settable.**
- No owning-vehicle concept here (this *is* the vehicle).

## 3. Ownership form — `OwnershipController` (Task 11c)

Does **not** use `@ModelAttribute`. `submit` reads a raw
`MultiValueMap<String, String> params` and pulls out only five named
keys by hand: `action`, `add`, `remove`, `q`, and the parallel-array pair
`customerId[]`/`percentage[]`, plus `vehicleVersion`, `transferDate`,
`primary`. Everything else in the request body is ignored — there is no
DTO whose properties auto-bind, so no field beyond these named ones can
be injected by adding extra POST parameters. The vehicle itself is
identified solely by the `{id}` **path variable**, used both for
`ownersForm(id)`/`ownersForm(id, submission)` and passed to
`ownershipService.saveOwners(id, submission)`; no `vehicleId` travels
inside `OwnersSubmissionDto`, so an ownership row can't be redirected to
a different vehicle from the form body.

- **`vehicleVersion`**: read from `params`, used only for the vehicle's
  optimistic-lock check in `OwnershipService.saveOwners` (`Vehicle` has no
  `setVersion`, so it's read-only use here too, same as above).
- No ownership row `id` is ever bound from the save form — rows are
  matched to existing ownerships by `customerId`, not by ownership id, so
  there's no `id` parameter to spoof on this endpoint at all.

## 4. Policy form — `PolicyController` (Task 11d, reused by Task 12)

`create`/`update` bind with `@ModelAttribute("policy") PolicyFormDto`:
`id`, `vehicleId`, `policyNumber`, `insuranceCompany`, `intermediaryId`,
`startDate`, `endDate`, `premium`, `surcharge`, `surchargeType`,
`version`.

This is the one form whose DTO carries a **vehicle id as a plain
bindable field** (`PolicyFormDto.vehicleId`), because it doubles as the
Task 15 view DTO source and the `toFormDto`/`toDto` mapper shapes. Checked
whether that field can move a policy to another vehicle:

- **`create`** (`POST /vehicles/{vehicleId}/policies`): the vehicle comes
  from the `{vehicleId}` **path variable** (`PolicyService.create(Long
  vehicleId, PolicyFormDto form)` → `vehicle(vehicleId)` →
  `policy.setVehicle(vehicle)`). `PolicyMapper.updateEntity` has
  `@Mapping(target = "vehicle", ignore = true)`, so `form.vehicleId()` is
  read into `values` but never applied to the entity. **A posted
  `vehicleId` in the body is ignored on create; the path segment wins.**
- **`update`** (`POST /policies/{id}`): `PolicyService.update` loads the
  policy by the `{id}` path variable, and explicitly keeps
  `Long vehicleId = policy.getVehicle().getId()` **from the loaded
  entity**, not from the form, for both the mobile-rule check and the
  return value used to build the redirect. The Javadoc says so
  explicitly: "its vehicle is ignored, since a policy never moves to
  another [vehicle]". `updateEntity`'s `vehicle` mapping is ignored here
  too. **Not settable — confirmed both in the service logic and the
  mapper.**
- **`id`**: bindable, unused — both `create`/`update` locate the entity
  via the path variable, `apply()`/`updateEntity` never touch the id, and
  `Policy` has no `setId`.
- **`version`**: bindable, used only for the optimistic-lock comparison
  in `update`; `Policy` has no `setVersion`.

**Net finding for Task 11d:** the extra `vehicleId` field on
`PolicyFormDto` is inert on both write paths — it's read (`values`) but
deliberately never written to the entity in either `create` or `update`.
No way found to move a policy to a different vehicle by adding
`vehicleId` to a POST.

## 5. Summary table

| Form | Binding style | `id` settable? | `version` settable? | vehicle id settable? |
|---|---|---|---|---|
| Customer (11a) | `@ModelAttribute` (full DTO) | No — path var wins, no setter | No — read-only lock check, no setter | n/a |
| Vehicle (11b) | `@ModelAttribute` (full DTO) | No — path var wins, no setter | No — read-only lock check, no setter | n/a |
| Ownership (11c) | Manual `MultiValueMap` read | n/a — no id field bound at all | No — read-only lock check, no setter | No — vehicle from path var only, never from body |
| Policy (11d/12) | `@ModelAttribute` (full DTO, includes `vehicleId`) | No — path var wins, no setter | No — read-only lock check, no setter | No — mapper ignores it, service reloads it from the entity |

## 6. Delete code paths

| Entity | Trigger | Service method | `@PreAuthorize` |
|---|---|---|---|
| Customer | `POST /customers/{id}/delete` → `CustomerController.delete` | `CustomerService.delete(Long)` (`service/CustomerService.java:225`) | Yes — `@PreAuthorize(Roles.ADMINISTRATOR_ONLY)` at line 223 |
| Customer (preview) | `GET /customers/{id}/delete` → `CustomerController.confirmDelete` | `CustomerService.deletionPreview(Long)` | Yes — line 196 |
| Vehicle | `POST /vehicles/{id}/delete` → `VehicleController.delete` | `VehicleService.delete(Long)` (`service/VehicleService.java:194`) | Yes — line 192 |
| Vehicle (preview) | `GET /vehicles/{id}/delete` → `VehicleController.confirmDelete` | `VehicleService.deletionPreview(Long)` | Yes — line 164 |
| Policy | `POST /policies/{id}/delete` → `PolicyController.delete` | `PolicyService.delete(Long)` (`service/PolicyService.java:229`) | Yes — line 227 |
| Policy (preview) | `GET /policies/{id}/delete` → `PolicyController.confirmDelete` | `PolicyService.deletionPreview(Long)` | Yes — line 212 |
| Ownership (closed row) | `POST /ownerships/{id}/delete` → `OwnershipController.delete` | `OwnershipService.delete(Long)` (`service/OwnershipService.java:261`) | Yes — line 259 |
| Ownership (preview) | `GET /ownerships/{id}/delete` → `OwnershipController.confirmDelete` | `OwnershipService.deletionPreview(Long)` | Yes — line 236, blocks current rows |
| Ownership (former owners on re-import) | `ExcelImporterService.syncOwnerships` (`importer/ExcelImporterService.java:387`, `ownershipRepository.delete(former)`) | **None — calls the repository directly** | **No `@PreAuthorize`** |

All four HTTP-reachable delete actions (customer, vehicle, policy,
ownership) go through a service method carrying
`@PreAuthorize(Roles.ADMINISTRATOR_ONLY)`, and so do their confirmation
previews, so a ΥΠΑΛΛΗΛΟΣ hitting `POST /vehicles/{id}/delete` (etc.)
directly — without the button ever being rendered — is rejected before
any repository call. `SecurityConfig` also requires every request to be
authenticated, and `@EnableMethodSecurity` is turned on.

The one delete call **not** behind a guard is
`ExcelImporterService.syncOwnerships`, which drops ownership rows the
Excel file no longer lists as current owners. This is the pre-existing
Excel import path, not a Task 11 form: it runs only from the standalone
`import` Spring profile (`spring-boot:run -Dspring-boot.run.profiles=import`),
which per `SecurityConfig` has no web filter chain at all (no HTTP
endpoint reaches it), and per CLAUDE.md's resolved conflict §6 runs with
nobody logged in. It calls `ownershipRepository.delete(...)` directly
rather than through `OwnershipService`, so it also bypasses the
Task 11e rule that only a *closed* ownership row may be deleted — but
that rule exists to protect the 100%/one-primary invariant from a
one-row-at-a-time UI edit, and `syncOwnerships` replaces a vehicle's
*entire* current-owner set in one pass (add/update/remove together), so
the invariant risk the UI rule guards against doesn't apply the same way
here. Flagging it only because the task asked for every delete path,
not because it's reachable by an unprivileged user.

## 7. Not investigated

- CSRF and session/login behavior around these endpoints (Task 10 scope,
  not re-checked here).
- Field-level `@Valid`/binder-level rejects (`BindingResult`) versus the
  service-level `Violations` checks — both exist on Vehicle/Policy forms
  but weren't the subject of this pass.
