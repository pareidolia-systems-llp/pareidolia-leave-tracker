package com.acme.hr.leavetracker.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BalanceUpdate(@NotNull @DecimalMin("0.0") @Digits(integer = 4, fraction = 1) BigDecimal entitlementDays) { }
