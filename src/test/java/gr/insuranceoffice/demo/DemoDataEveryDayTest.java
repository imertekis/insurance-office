package gr.insuranceoffice.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import gr.insuranceoffice.demo.DemoData.Owners;
import gr.insuranceoffice.demo.DemoData.Plan;
import gr.insuranceoffice.demo.DemoData.PolicyPlan;
import gr.insuranceoffice.demo.DemoData.VehiclePlan;
import gr.insuranceoffice.service.PolicyService;

/**
 * Task 40: the demo's plan, made on every day of a fixed range, gives
 * policies that {@code PolicyService.create} accepts, in the order
 * {@link DemoSeeder} saves them. The dates are counted from the day of the
 * fill, so a plan that passes on one day can break on the next; the range
 * holds two leap years.
 * <p>
 * The rules are written out here, as the overlap rule is a query and needs a
 * database; {@link DemoSeedOnAnotherDayTest} ties them to the real service.
 */
class DemoDataEveryDayTest {

	private static final LocalDate FIRST_DAY = LocalDate.of(2024, 1, 1);

	private static final LocalDate LAST_DAY = LocalDate.of(2030, 12, 31);

	private static final ZoneId ATHENS = ZoneId.of("Europe/Athens");

	/** The failing days the message shows in full; the rest are counted. */
	private static final int SHOWN = 10;

	@Test
	void everyPolicyOfEveryDayPassesTheRulesOfPolicyService() {
		Map<LocalDate, List<String>> failing = new LinkedHashMap<>();
		for (LocalDate day = FIRST_DAY; !day.isAfter(LAST_DAY); day = day.plusDays(1)) {
			// The day as the seeder reads it, from the application's clock.
			Clock clock = Clock.fixed(day.atTime(LocalTime.NOON).atZone(ATHENS).toInstant(), ATHENS);
			LocalDate today = LocalDate.now(clock);
			List<String> problems = problems(DemoData.plan(DemoData.SEED, today), today);
			if (!problems.isEmpty()) {
				failing.put(day, problems);
			}
		}

		assertThat(failing).as(() -> failing.size() + " days of " + FIRST_DAY + " – " + LAST_DAY + " fail; the first:\n"
				+ failing.entrySet().stream().limit(SHOWN)
						.map(day -> day.getKey() + ": " + String.join("; ", day.getValue()))
						.collect(Collectors.joining("\n")))
				.isEmpty();
	}

	/** What PolicyService.create would refuse, policy by policy, as the seeder saves them. */
	private static List<String> problems(Plan plan, LocalDate today) {
		List<String> problems = new ArrayList<>();
		Set<String> numbers = new HashSet<>();
		for (int v = 0; v < plan.vehicles().size(); v++) {
			VehiclePlan vehicle = plan.vehicles().get(v);
			// The owners are saved before the policies: the primary owner is
			// the first of the last owners.
			Owners owners = vehicle.owners().getLast();
			boolean primaryHasMobile = plan.customers().get(owners.customers().getFirst()).mobile() != null;
			List<PolicyPlan> saved = new ArrayList<>();
			for (PolicyPlan policy : vehicle.policies()) {
				String which = "vehicle " + v + ", policy " + policy.policyNumber() + " " + period(policy);
				if (!policy.endDate().isAfter(policy.startDate())) {
					problems.add(which + " ends before it starts");
				}
				// PolicyRepository.findOverlapping: touching is not overlapping.
				for (PolicyPlan other : saved) {
					if (policy.startDate().isBefore(other.endDate()) && other.startDate().isBefore(policy.endDate())) {
						problems.add(which + " overlaps " + other.policyNumber() + " " + period(other));
					}
				}
				if (!numbers.add(policy.policyNumber())) {
					problems.add(which + " has a number already used");
				}
				BigDecimal premium = new BigDecimal(policy.premium().replace(',', '.'));
				if (premium.signum() <= 0 || premium.compareTo(PolicyService.MAX_PREMIUM) >= 0
						|| premium.stripTrailingZeros().scale() > 2) {
					problems.add(which + " has the premium " + policy.premium());
				}
				// DECISIONS §1: a policy in force today needs a primary owner with a mobile.
				boolean current = !today.isBefore(policy.startDate()) && !today.isAfter(policy.endDate());
				if (current && !primaryHasMobile) {
					problems.add(which + " is in force, and its primary owner has no mobile");
				}
				saved.add(policy);
			}
		}
		return problems;
	}

	private static String period(PolicyPlan policy) {
		return "(" + policy.startDate() + " – " + policy.endDate() + ")";
	}

}
