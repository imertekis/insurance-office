package gr.insuranceoffice.demo;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.demo.DemoData.Owners;
import gr.insuranceoffice.demo.DemoData.Plan;
import gr.insuranceoffice.demo.DemoData.PolicyPlan;
import gr.insuranceoffice.demo.DemoData.VehiclePlan;
import gr.insuranceoffice.dto.CustomerDto;
import gr.insuranceoffice.dto.OwnersSubmissionDto;
import gr.insuranceoffice.dto.PolicyFormDto;
import gr.insuranceoffice.entity.AppUser;
import gr.insuranceoffice.entity.AppUser.Role;
import gr.insuranceoffice.entity.Intermediary;
import gr.insuranceoffice.repository.AppUserRepository;
import gr.insuranceoffice.repository.AuditLogRepository;
import gr.insuranceoffice.repository.CustomerRepository;
import gr.insuranceoffice.repository.IntermediaryRepository;
import gr.insuranceoffice.repository.PolicyRepository;
import gr.insuranceoffice.repository.VehicleRepository;
import gr.insuranceoffice.security.PasswordPolicy;
import gr.insuranceoffice.service.CustomerService;
import gr.insuranceoffice.service.OwnershipService;
import gr.insuranceoffice.service.PolicyService;
import gr.insuranceoffice.service.VehicleService;

/**
 * Saves the plan of {@link DemoData} (Task 39b) through the services, so the
 * data pass the rules of the forms and are written to audit_log. Two things
 * have no service, as no screen makes them, and are saved through their
 * repositories as the import and the create-user profile save them: the
 * intermediaries and the two accounts.
 * <p>
 * One transaction: an empty database, or all of it. A half-filled one would
 * be refused at the next start and stay half-filled.
 */
@Component
@Profile("demo")
public class DemoSeeder {

	private static final SecureRandom RANDOM = new SecureRandom();

	// No 0/O, 1/l/I: the password is read off a terminal and typed.
	private static final String PASSWORD_CHARACTERS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final int PASSWORD_LENGTH = 16;

	/**
	 * An account of the demo. Its password is random, made on this run and
	 * nowhere else: the runner prints it once.
	 */
	public record Account(String username, Role role, String password) {
	}

	/** What a fill made, for the runner to print. */
	public record Seeded(List<Account> accounts, int customers, int vehicles, int policies) {
	}

	private final CustomerService customerService;
	private final VehicleService vehicleService;
	private final OwnershipService ownershipService;
	private final PolicyService policyService;
	private final CustomerRepository customerRepository;
	private final VehicleRepository vehicleRepository;
	private final PolicyRepository policyRepository;
	private final IntermediaryRepository intermediaryRepository;
	private final AppUserRepository appUserRepository;
	private final AuditLogRepository auditLogRepository;
	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	public DemoSeeder(CustomerService customerService, VehicleService vehicleService,
			OwnershipService ownershipService, PolicyService policyService, CustomerRepository customerRepository,
			VehicleRepository vehicleRepository, PolicyRepository policyRepository,
			IntermediaryRepository intermediaryRepository, AppUserRepository appUserRepository,
			AuditLogRepository auditLogRepository, PasswordEncoder passwordEncoder, Clock clock) {
		this.customerService = customerService;
		this.vehicleService = vehicleService;
		this.ownershipService = ownershipService;
		this.policyService = policyService;
		this.customerRepository = customerRepository;
		this.vehicleRepository = vehicleRepository;
		this.policyRepository = policyRepository;
		this.intermediaryRepository = intermediaryRepository;
		this.appUserRepository = appUserRepository;
		this.auditLogRepository = auditLogRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/**
	 * Fills the database with the data of {@link DemoData#SEED} and two
	 * accounts, if it holds nothing: no customer, vehicle, policy,
	 * intermediary, account or audit_log row.
	 *
	 * @return what was made, or empty when the database already had data and
	 *         nothing was written
	 */
	@Transactional
	public Optional<Seeded> seedIfEmpty() {
		if (customerRepository.count() > 0 || vehicleRepository.count() > 0 || policyRepository.count() > 0
				|| intermediaryRepository.count() > 0 || appUserRepository.count() > 0
				|| auditLogRepository.count() > 0) {
			return Optional.empty();
		}
		// The application's today, the one the services check the data against.
		Plan plan = DemoData.plan(DemoData.SEED, LocalDate.now(clock));

		List<Long> intermediaries = plan.intermediaries().stream().map(planned -> {
			Intermediary intermediary = new Intermediary();
			intermediary.setFullName(planned.fullName());
			intermediary.setEmail(planned.email());
			return intermediaryRepository.save(intermediary).getId();
		}).toList();

		List<Long> customers = new ArrayList<>();
		for (CustomerDto customer : plan.customers()) {
			customers.add(customerService.create(customer).customer().id());
		}

		int policies = 0;
		for (VehiclePlan planned : plan.vehicles()) {
			Long vehicleId = vehicleService.create(planned.vehicle()).id();
			for (Owners owners : planned.owners()) {
				List<Long> ids = owners.customers().stream().map(customers::get).toList();
				ownershipService.saveOwners(vehicleId, new OwnersSubmissionDto(vehicleService.find(vehicleId).version(),
						owners.transferDate(), ids.getFirst(), ids, owners.percentages()));
			}
			for (PolicyPlan policy : planned.policies()) {
				policyService.create(vehicleId, new PolicyFormDto(null, vehicleId, policy.policyNumber(),
						policy.insuranceCompany(),
						policy.intermediary() == null ? null : intermediaries.get(policy.intermediary()),
						policy.startDate(), policy.endDate(), policy.premium(), policy.surchargeType() != null,
						policy.surchargeType(), null));
				policies++;
			}
		}

		List<Account> accounts = List.of(account("admin", "Διαχειριστής (demo)", Role.ΔΙΑΧΕΙΡΙΣΤΗΣ),
				account("clerk", "Υπάλληλος (demo)", Role.ΥΠΑΛΛΗΛΟΣ));
		return Optional.of(new Seeded(accounts, customers.size(), plan.vehicles().size(), policies));
	}

	private Account account(String username, String fullName, Role role) {
		String password = randomPassword(username);
		AppUser user = new AppUser();
		user.setUsername(username);
		user.setFullName(fullName);
		user.setRole(role);
		user.setPasswordHash(passwordEncoder.encode(password));
		appUserRepository.save(user);
		return new Account(username, role, password);
	}

	/** Random, never from the seed, and within the rule of Task 22a. */
	private static String randomPassword(String username) {
		while (true) {
			StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
			for (int i = 0; i < PASSWORD_LENGTH; i++) {
				password.append(PASSWORD_CHARACTERS.charAt(RANDOM.nextInt(PASSWORD_CHARACTERS.length())));
			}
			if (PasswordPolicy.check(password.toString(), username).isEmpty()) {
				return password.toString();
			}
		}
	}

}
