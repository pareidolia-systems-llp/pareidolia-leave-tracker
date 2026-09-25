alter table leave_balances
    modify column entitlement_days decimal(5,1) not null;

alter table leave_balances
    modify column used_days decimal(5,1) not null default 0.0;

alter table leave_requests
    modify column total_days decimal(5,1) not null;
