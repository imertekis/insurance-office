package gr.insuranceoffice.controller;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.OptimisticLockException;

import gr.insuranceoffice.service.BusinessException;
import gr.insuranceoffice.service.NotFoundException;

/**
 * Turns service exceptions into RFC 9457 problem responses for the JSON
 * endpoints. Limited to {@link RestController}s: the Thymeleaf views from
 * Task 8 on show these errors inside the page instead.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

	// SPEC §9: the second writer gets a clear message and nothing is lost.
	@ExceptionHandler({ OptimisticLockingFailureException.class, OptimisticLockException.class })
	ProblemDetail conflict(RuntimeException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Η εγγραφή άλλαξε από άλλον χρήστη ενώ την επεξεργαζόσασταν. "
						+ "Φορτώστε την ξανά για να δείτε τις αλλαγές του και επαναλάβετε τη δική σας.");
		problem.setTitle("Ταυτόχρονη αλλαγή");
		return problem;
	}

	@ExceptionHandler(BusinessException.class)
	ProblemDetail invalid(BusinessException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
				"Η εγγραφή δεν αποθηκεύτηκε: διορθώστε τα πεδία που επισημαίνονται.");
		problem.setTitle("Μη έγκυρα στοιχεία");
		problem.setProperty("violations", exception.getViolations());
		return problem;
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Δεν βρέθηκε");
		return problem;
	}

}
