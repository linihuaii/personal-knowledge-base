create table app_metadata (
  metadata_key varchar(120) primary key,
  metadata_value varchar(1000),
  updated_at timestamp default current_timestamp not null
);
