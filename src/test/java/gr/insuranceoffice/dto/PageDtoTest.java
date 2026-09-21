package gr.insuranceoffice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class PageDtoTest {

	@Test
	void countsThePagesAndNeverFewerThanOne() {
		assertThat(page(0, 1, 0).getTotalPages()).isEqualTo(1);
		assertThat(page(1, 1, 1).getTotalPages()).isEqualTo(1);
		assertThat(page(50, 1, 50).getTotalPages()).isEqualTo(1);
		assertThat(page(50, 1, 51).getTotalPages()).isEqualTo(2);
		assertThat(page(50, 1, 100).getTotalPages()).isEqualTo(2);
		assertThat(page(50, 1, 101).getTotalPages()).isEqualTo(3);
	}

	@Test
	void saysWhichRowsThePageShows() {
		PageDto<Integer> second = page(20, 2, 120);

		assertThat(second.getFirstItem()).isEqualTo(51);
		assertThat(second.getLastItem()).isEqualTo(70);
		assertThat(page(0, 1, 0).getFirstItem()).isZero();
		assertThat(page(0, 1, 0).getLastItem()).isZero();
	}

	@Test
	void knowsTheFirstAndTheLastPage() {
		assertThat(page(50, 1, 120).isFirst()).isTrue();
		assertThat(page(50, 1, 120).isLast()).isFalse();
		assertThat(page(20, 3, 120).isFirst()).isFalse();
		assertThat(page(20, 3, 120).isLast()).isTrue();
		assertThat(page(0, 1, 0).isFirst()).isTrue();
		assertThat(page(0, 1, 0).isLast()).isTrue();
	}

	@Test
	void showsTwoPagesEitherSideButNoneThatDoNotExist() {
		assertThat(window(1, 3)).containsExactly(1, 3);
		assertThat(window(1, 12)).containsExactly(1, 3);
		assertThat(window(6, 12)).containsExactly(4, 8);
		assertThat(window(12, 12)).containsExactly(10, 12);
		assertThat(window(1, 1)).containsExactly(1, 1);
	}

	@Test
	void readsAnOrderFromTheUrlAndFallsBackForAnythingElse() {
		assertThat(SortDirection.fromParam("asc", SortDirection.DESC)).isEqualTo(SortDirection.ASC);
		assertThat(SortDirection.fromParam("desc", SortDirection.ASC)).isEqualTo(SortDirection.DESC);
		assertThat(SortDirection.fromParam(null, SortDirection.DESC)).isEqualTo(SortDirection.DESC);
		assertThat(SortDirection.fromParam("DESC", SortDirection.ASC)).isEqualTo(SortDirection.ASC);
		assertThat(SortDirection.fromParam("sideways", SortDirection.ASC)).isEqualTo(SortDirection.ASC);
		assertThat(SortDirection.ASC.reversed()).isEqualTo(SortDirection.DESC);
		assertThat(SortDirection.DESC.reversed()).isEqualTo(SortDirection.ASC);
	}

	private static PageDto<Integer> page(int rows, int number, long total) {
		List<Integer> items = IntStream.range(0, rows).boxed().toList();
		return new PageDto<>(items, number, 50, total, SortDirection.ASC);
	}

	// The first and last page number the pager shows around a page.
	private static List<Integer> window(int number, int totalPages) {
		PageDto<Integer> page = new PageDto<>(List.of(1), number, 50, totalPages * 50L, SortDirection.ASC);
		return List.of(page.getWindowStart(), page.getWindowEnd());
	}

}
