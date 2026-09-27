package gr.insuranceoffice.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock the tests move on by hand, so the five minutes of a lockout take none. */
final class MutableClock extends Clock {

	static final ZoneId ATHENS = ZoneId.of("Europe/Athens");

	private Instant now = Instant.parse("2026-09-27T09:00:00Z");

	synchronized void advance(Duration duration) {
		now = now.plus(duration);
	}

	@Override
	public synchronized Instant instant() {
		return now;
	}

	@Override
	public ZoneId getZone() {
		return ATHENS;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

}
