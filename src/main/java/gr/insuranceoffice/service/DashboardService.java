package gr.insuranceoffice.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gr.insuranceoffice.dto.DashboardDto;
import gr.insuranceoffice.dto.ExpiryPeriod;
import gr.insuranceoffice.mapper.DashboardMapper;
import gr.insuranceoffice.repository.PolicyRepository;

/**
 * The expiry screen (SPEC §7.1): the renewals still to do. A policy whose
 * vehicle already has a later policy has been renewed and is left out, in
 * every view.
 */
@Service
public class DashboardService {

	/** How far back "already expired" looks: older losses are not chased. */
	static final int EXPIRED_LOOK_BACK_DAYS = 90;

	private final PolicyRepository policyRepository;

	private final DashboardMapper dashboardMapper;

	private final Clock clock;

	public DashboardService(PolicyRepository policyRepository, DashboardMapper dashboardMapper, Clock clock) {
		this.policyRepository = policyRepository;
		this.dashboardMapper = dashboardMapper;
		this.clock = clock;
	}

	/**
	 * @param insuranceCompany exact name; null or blank for every company
	 */
	@Transactional(readOnly = true)
	public DashboardDto expiries(ExpiryPeriod period, String insuranceCompany) {
		LocalDate today = LocalDate.now(clock);
		// A policy is still in force on its end date, so it is expiring today,
		// not expired.
		LocalDate from = period.isExpired() ? today.minusDays(EXPIRED_LOOK_BACK_DAYS) : today;
		LocalDate to = period.isExpired() ? today.minusDays(1) : today.plusDays(period.getDays());
		String company = insuranceCompany == null || insuranceCompany.isBlank() ? null : insuranceCompany;
		return new DashboardDto(period, company, from, to,
				dashboardMapper.toDtoList(policyRepository.findNotRenewedEndingBetween(from, to, company)),
				policyRepository.findInsuranceCompanies());
	}

}
