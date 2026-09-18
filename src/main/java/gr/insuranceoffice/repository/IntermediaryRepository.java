package gr.insuranceoffice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import gr.insuranceoffice.entity.Intermediary;

public interface IntermediaryRepository extends JpaRepository<Intermediary, Long> {

	Optional<Intermediary> findByFullName(String fullName);

	/**
	 * What the policy form offers: the active intermediaries, plus the one a
	 * policy already names even if no longer active, so editing an old policy
	 * does not lose it.
	 */
	@Query("select i from Intermediary i where i.active = true or i.id = :selectedId order by i.fullName")
	List<Intermediary> findSelectable(Long selectedId);

}
