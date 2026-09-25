create table employees (
    id binary(16) not null,
    email varchar(320) not null unique,
    full_name varchar(200) not null,
    manager_email varchar(320) not null,
    active boolean not null default true,
    created_at datetime(6) not null,
    primary key (id)
);

create table leave_balances (
    id binary(16) not null,
    employee_id binary(16) not null,
    leave_type varchar(30) not null,
    entitlement_days integer not null,
    used_days integer not null default 0,
    version bigint not null default 0,
    constraint uk_leave_balance_employee_type unique (employee_id, leave_type),
    constraint ck_leave_balance_entitlement_nonnegative check (entitlement_days >= 0),
    constraint ck_leave_balance_used_nonnegative check (used_days >= 0),
    primary key (id),
    constraint fk_leave_balance_employee foreign key (employee_id) references employees(id)
);

create table leave_requests (
    id binary(16) not null,
    employee_id binary(16) not null,
    approver_email varchar(320) not null,
    leave_type varchar(30) not null,
    start_date date not null,
    end_date date not null,
    total_days integer not null,
    reason varchar(1000) not null,
    status varchar(30) not null,
    manager_comment varchar(1000),
    requested_at datetime(6) not null,
    decided_at datetime(6),
    approval_token_hash varchar(64),
    approval_token_expires_at datetime(6),
    constraint ck_leave_request_dates check (end_date >= start_date),
    constraint ck_leave_request_total_days check (total_days > 0),
    primary key (id),
    constraint fk_leave_request_employee foreign key (employee_id) references employees(id)
);

create index ix_leave_requests_employee_requested on leave_requests(employee_id, requested_at);
create index ix_leave_requests_status on leave_requests(status);

create table leave_audit_events (
    id binary(16) not null,
    leave_request_id binary(16) not null,
    event_type varchar(40) not null,
    actor varchar(320) not null,
    details varchar(1000),
    occurred_at datetime(6) not null,
    primary key (id),
    constraint fk_leave_audit_request foreign key (leave_request_id) references leave_requests(id)
);

create index ix_leave_audit_request on leave_audit_events(leave_request_id, occurred_at);
