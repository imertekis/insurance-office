package gr.insuranceoffice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import gr.insuranceoffice.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByUsername(String username);

}
