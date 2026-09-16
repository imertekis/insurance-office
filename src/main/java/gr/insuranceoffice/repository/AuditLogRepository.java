package gr.insuranceoffice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import gr.insuranceoffice.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

}
