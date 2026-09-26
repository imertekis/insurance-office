package gr.insuranceoffice.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Until a password that no longer meets the rule is changed, every page
 * leads to «Αλλαγή κωδικού» (Task 22a, decision 1): what was asked for, a
 * form sent from another tab included, is not served. The change page
 * itself, logging out, the static files and the error page still are.
 */
public class ForcedPasswordChangeFilter extends OncePerRequestFilter {

	static final String CHANGE_PAGE = "/account/password";

	private static final RequestMatcher STILL_ALLOWED = new OrRequestMatcher(
			PathPatternRequestMatcher.pathPattern(CHANGE_PAGE),
			PathPatternRequestMatcher.pathPattern("/logout"),
			PathPatternRequestMatcher.pathPattern("/login"),
			PathPatternRequestMatcher.pathPattern("/error"),
			PathPatternRequestMatcher.pathPattern("/css/**"),
			PathPatternRequestMatcher.pathPattern("/js/**"),
			PathPatternRequestMatcher.pathPattern("/webjars/**"),
			PathPatternRequestMatcher.pathPattern("/favicon.ico"),
			PathPatternRequestMatcher.pathPattern("/favicon.svg"));

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (ForcedPasswordChange.required() && !STILL_ALLOWED.matches(request)) {
			response.sendRedirect(request.getContextPath() + CHANGE_PAGE);
			return;
		}
		chain.doFilter(request, response);
	}

}
