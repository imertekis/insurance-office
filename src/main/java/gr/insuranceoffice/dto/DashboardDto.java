package gr.insuranceoffice.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The expiry screen: the chosen filters, the date range they cover (both
 * days included), the policies in it and every insurance company to filter by.
 *
 * @param insuranceCompany the chosen company, or null for all
 */
public record DashboardDto(
		ExpiryPeriod period,
		String insuranceCompany,
		LocalDate from,
		LocalDate to,
		List<ExpiringPolicyDto> policies,
		List<String> insuranceCompanies) {
}
