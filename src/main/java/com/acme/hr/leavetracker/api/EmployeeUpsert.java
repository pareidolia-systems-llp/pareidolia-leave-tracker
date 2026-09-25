package com.acme.hr.leavetracker.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EmployeeUpsert(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email String managerEmail,
        @NotNull @DecimalMin("0.0") @Digits(integer = 4, fraction = 1) BigDecimal plLeaveDays,
        @NotNull @DecimalMin("0.0") @Digits(integer = 4, fraction = 1) BigDecimal clLeaveDays,
        @NotNull @DecimalMin("0.0") @Digits(integer = 4, fraction = 1) BigDecimal slLeaveDays
) { }
