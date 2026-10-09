create table study_plan (
  id varchar(36) primary key,
  knowledge_base_id varchar(36) not null references knowledge_base(id),
  goal varchar(500) not null,
  current_level varchar(1000) not null,
  deadline date not null,
  daily_minutes integer not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create index ix_study_plan_updated on study_plan(updated_at);

create table study_task (
  id varchar(36) primary key,
  plan_id varchar(36) not null references study_plan(id) on delete cascade,
  title varchar(300) not null,
  description clob not null,
  scheduled_date date not null,
  estimated_minutes integer not null,
  completed boolean not null default false,
  sort_order integer not null,
  created_at timestamp with time zone not null
);

create index ix_study_task_plan_order on study_task(plan_id, sort_order);

create table study_task_source (
  task_id varchar(36) not null references study_task(id) on delete cascade,
  chunk_id varchar(36) not null references document_chunk(id),
  document_id varchar(36) not null references document(id),
  primary key(task_id, chunk_id)
);
