package com.acme.hr.leavetracker.repository;

import com.acme.hr.leavetracker.domain.LeaveRequest;
import com.acme.hr.leavetracker.domain.LeaveStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r join fetch r.employee where r.id = :id")
    Optional<LeaveRequest> lockById(@Param("id") UUID id);

    @Query("""
            select r from LeaveRequest r
            where r.employee.id = :employeeId
              and r.status in :statuses
              and r.startDate <= :endDate
              and r.endDate >= :startDate
            """)
    List<LeaveRequest> findOverlapping(@Param("employeeId") UUID employeeId,
                                       @Param("statuses") Collection<LeaveStatus> statuses,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    @Query("select r from LeaveRequest r join fetch r.employee order by r.requestedAt desc")
    List<LeaveRequest> findAllForHr();
}
