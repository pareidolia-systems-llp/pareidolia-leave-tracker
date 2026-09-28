create table leave_supporting_documents (
    id binary(16) not null,
    leave_request_id binary(16) not null,
    storage_key varchar(200) not null,
    original_filename varchar(255) not null,
    content_type varchar(100) not null,
    file_size bigint not null,
    uploaded_at datetime(6) not null,
    primary key (id),
    constraint uk_leave_supporting_document_request unique (leave_request_id),
    constraint uk_leave_supporting_document_key unique (storage_key),
    constraint fk_leave_supporting_document_request foreign key (leave_request_id) references leave_requests(id)
);
