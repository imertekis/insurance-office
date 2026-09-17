package gr.insuranceoffice.dto;

import java.util.List;

/**
 * A saved customer, with the new version to send on the next save, and the
 * warnings that did not block saving (e.g. missing ΑΦΜ, DECISIONS §2).
 */
public record SavedCustomerDto(CustomerDto customer, List<String> warnings) {
}
