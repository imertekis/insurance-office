package gr.insuranceoffice.dto;

import java.util.List;

/**
 * The policy list (Task 15): a page of policies for one insurance company or
 * for all, and every company to filter by, as on the expiry screen.
 *
 * @param insuranceCompany the chosen company, or null for all
 */
public record PolicyListDto(
		PageDto<PolicyViewDto> policies,
		String insuranceCompany,
		List<String> insuranceCompanies) {
}
