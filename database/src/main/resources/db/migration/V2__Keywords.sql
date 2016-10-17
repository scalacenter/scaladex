create table scaladex.Keywords(
  id identity primary key,
  name varchar(1024) not null
);

create table Projects_Keywords(
  project_id int not null,
  foreign key(project_id) references scaladex.Projects(id),
  keyword_id int not null,
  foreign key(keyword_id) references scaladex.Keywords(id),
  primary key(project_id, keyword_id)
);