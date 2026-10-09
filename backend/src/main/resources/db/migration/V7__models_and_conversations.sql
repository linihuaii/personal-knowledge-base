create table conversation (
  id varchar(36) primary key,
  knowledge_base_id varchar(36) not null references knowledge_base(id),
  title varchar(255) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create index ix_conversation_kb_updated on conversation(knowledge_base_id, updated_at);

create table chat_message (
  id varchar(36) primary key,
  conversation_id varchar(36) not null references conversation(id),
  role varchar(20) not null,
  content clob not null,
  mode varchar(30),
  status varchar(20) not null,
  created_at timestamp with time zone not null
);

create index ix_chat_message_conversation on chat_message(conversation_id, created_at);

create table chat_citation (
  id varchar(36) primary key,
  message_id varchar(36) not null references chat_message(id),
  chunk_id varchar(36) not null,
  document_id varchar(36) not null,
  ordinal integer not null,
  score double precision not null
);

create index ix_chat_citation_message on chat_citation(message_id, ordinal);
