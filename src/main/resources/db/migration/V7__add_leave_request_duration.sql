alter table leave_requests
    add column duration varchar(20) not null default 'FULL_DAY';
