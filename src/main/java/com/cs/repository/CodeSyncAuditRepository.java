package com.cs.repository;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cs.dto.TopClientDto;
import com.cs.entity.CodeSyncAudit;

public interface CodeSyncAuditRepository extends JpaRepository<CodeSyncAudit, Long> {

	@Query("SELECT COUNT(a) FROM CodeSyncAudit a WHERE a.createdAt >= :start AND a.createdAt < :end")
	long countBetween(@Param("start") Timestamp start, @Param("end") Timestamp end);

	@Query("SELECT COUNT(DISTINCT a.clientIp) FROM CodeSyncAudit a WHERE a.createdAt >= :start AND a.createdAt < :end")
	long activeClientsBetween(@Param("start") Timestamp start, @Param("end") Timestamp end);

	List<CodeSyncAudit> findByCreatedAtBetweenOrderByCreatedAtDesc(Timestamp start, Timestamp end);

	Page<CodeSyncAudit> findByCreatedAtBetweenOrderByCreatedAtDesc(Timestamp start, Timestamp end, Pageable pageable);

	@Query("SELECT new com.cs.dto.TopClientDto(a.clientIp, c.name, COUNT(a)) FROM CodeSyncAudit a LEFT JOIN "
			+ "CodeSyncClient c ON a.clientIp = c.ip WHERE a.createdAt >= :start AND a.createdAt < :end "
			+ "GROUP BY a.clientIp, c.name ORDER BY COUNT(a) DESC")
	List<TopClientDto> topClients(@Param("start") Timestamp start, @Param("end") Timestamp end);

}