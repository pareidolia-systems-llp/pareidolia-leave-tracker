alter table employees
    add column joining_date date;

alter table employees
    add column employment_type varchar(20);

alter table employees
    add column probation_end_date date;

update employees
set joining_date = cast(created_at as date)
where joining_date is null;

update employees
set employment_type = 'REGULAR'
where employment_type is null;

alter table employees
    modify column joining_date date not null;

alter table employees
    modify column employment_type varchar(20) not null;
