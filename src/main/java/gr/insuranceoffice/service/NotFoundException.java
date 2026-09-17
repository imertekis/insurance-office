package gr.insuranceoffice.service;

/** The record to change does not exist, for example because it was deleted. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

}
