create table knowledge_base (
  id varchar(36) primary key,
  name varchar(120) not null,
  description varchar(2000) not null default '',
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  deleted_at timestamp with time zone
);
create unique index uq_knowledge_base_name on knowledge_base(name);
create table document (
  id varchar(36) primary key,
  knowledge_base_id varchar(36) not null references knowledge_base(id),
  original_name varchar(255) not null,
  media_type varchar(100) not null,
  size_bytes bigint not null,
  sha256 varchar(64) not null,
  storage_key varchar(100) not null,
  status varchar(32) not null,
  failure_code varchar(80),
  failure_message varchar(1000),
  parser_version varchar(30) not null,
  attempt_count integer not null default 0,
  lease_until timestamp with time zone,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);
create index ix_document_kb_status on document(knowledge_base_id,status);
create table document_chunk (
  id varchar(36) primary key,
  document_id varchar(36) not null references document(id),
  ordinal integer not null,
  chunk_text clob not null,
  start_offset integer not null,
  page_number integer,
  section_name varchar(255),
  pipeline_version varchar(30) not null,
  vector_json varchar(2000),
  unique(document_id,ordinal)
);
