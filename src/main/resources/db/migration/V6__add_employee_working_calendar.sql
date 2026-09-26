create table company_holidays (
    id binary(16) not null,
    holiday_date date not null,
    holiday_name varchar(200) not null,
    primary key (id),
    constraint uk_company_holiday_date unique (holiday_date)
);

create table employee_weekly_offs (
    id binary(16) not null,
    employee_id binary(16) not null,
    off_date date not null,
    primary key (id),
    constraint uk_employee_weekly_off_date unique (employee_id, off_date),
    constraint fk_employee_weekly_off_employee foreign key (employee_id) references employees(id)
);

create index ix_employee_weekly_off_employee_date on employee_weekly_offs(employee_id, off_date);
