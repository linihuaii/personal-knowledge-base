create table practice_session (
  id varchar(36) primary key,
  knowledge_base_id varchar(36) not null references knowledge_base(id),
  difficulty varchar(20) not null,
  question_types_json clob not null,
  document_ids_json clob not null,
  question_count integer not null,
  status varchar(20) not null,
  created_at timestamp with time zone not null,
  submitted_at timestamp with time zone
);

create index ix_practice_session_kb_created on practice_session(knowledge_base_id, created_at);

create table practice_question (
  id varchar(36) primary key,
  session_id varchar(36) not null references practice_session(id) on delete cascade,
  ordinal integer not null,
  question_type varchar(30) not null,
  stem clob not null,
  options_json clob,
  answer clob not null,
  explanation clob not null,
  sources_json clob not null,
  unique(session_id, ordinal)
);

create index ix_practice_question_session on practice_question(session_id, ordinal);

create table practice_answer (
  question_id varchar(36) primary key references practice_question(id) on delete cascade,
  submitted_answer clob not null,
  score double precision,
  feedback clob,
  scoring_status varchar(20) not null,
  submitted_at timestamp with time zone not null
);
