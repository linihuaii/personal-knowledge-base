create table study_task_source_v9 (
  task_id varchar(36) not null,
  chunk_id varchar(36) not null,
  document_id varchar(36) not null,
  document_name varchar(500) not null,
  location varchar(300) not null,
  excerpt clob not null,
  primary key(task_id, chunk_id),
  constraint fk_study_source_task foreign key(task_id) references study_task(id) on delete cascade
);

insert into study_task_source_v9(task_id,chunk_id,document_id,document_name,location,excerpt)
select s.task_id,s.chunk_id,s.document_id,d.original_name,
  case when c.page_number is not null then '第 ' || c.page_number || ' 页'
       when c.section_name is not null and c.section_name<>'' then c.section_name
       else '未标注位置' end,
  c.chunk_text
from study_task_source s
join document d on d.id=s.document_id
join document_chunk c on c.id=s.chunk_id and c.document_id=s.document_id;

drop table study_task_source;
alter table study_task_source_v9 rename to study_task_source;
