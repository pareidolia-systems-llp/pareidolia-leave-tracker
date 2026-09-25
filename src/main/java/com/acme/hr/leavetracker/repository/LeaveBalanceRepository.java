package com.acme.hr.leavetracker.repository;

import com.acme.hr.leavetracker.domain.LeaveBalance;
import com.acme.hr.leavetracker.domain.LeaveType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {
    List<LeaveBalance> findByEmployeeId(UUID employeeId);

    Optional<LeaveBalance> findByEmployeeIdAndLeaveType(UUID employeeId, LeaveType leaveType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LeaveBalance b where b.employee.id = :employeeId and b.leaveType = :leaveType")
    Optional<LeaveBalance> lockByEmployeeIdAndLeaveType(@Param("employeeId") UUID employeeId,
                                                          @Param("leaveType") LeaveType leaveType);
}
