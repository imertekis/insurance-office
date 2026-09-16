package gr.insuranceoffice.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * The ownership rule (SPEC §8, DATA_MODEL "ownership"): a vehicle's current
 * owners' shares sum to 100 and exactly one of them is primary. The database
 * cannot check a rule across rows, so every write path goes through here.
 */
@Service
public class OwnershipService {

	private static final BigDecimal FULL = new BigDecimal(100);

	/** One current owner's share of a vehicle. */
	public record Share(BigDecimal percentage, boolean primary) {
	}

	/**
	 * @param shares all current owners of one vehicle
	 * @return what breaks the rule, in Greek; empty when the owners are valid
	 */
	public List<String> checkCurrentOwners(List<Share> shares) {
		List<String> problems = new ArrayList<>();
		BigDecimal total = shares.stream().map(Share::percentage).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (total.compareTo(FULL) != 0) {
			problems.add("τα ποσοστά ιδιοκτησίας του οχήματος αθροίζουν " + percent(total)
					+ " αντί για 100%");
		}
		long primaries = shares.stream().filter(Share::primary).count();
		if (primaries != 1) {
			problems.add("το όχημα πρέπει να έχει ακριβώς έναν κύριο ιδιοκτήτη, έχει " + primaries);
		}
		return problems;
	}

	private static String percent(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
	}

}
