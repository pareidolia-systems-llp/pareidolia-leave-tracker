create table pl_monthly_accruals (
    id binary(16) not null,
    employee_id binary(16) not null,
    accrual_month date not null,
    credited_days decimal(5,1) not null,
    credited_at datetime(6) not null,
    primary key (id),
    constraint uk_pl_monthly_accrual_employee_month unique (employee_id, accrual_month),
    constraint fk_pl_monthly_accrual_employee foreign key (employee_id) references employees(id)
);
