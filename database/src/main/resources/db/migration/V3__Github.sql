create table scaladex.Projects_Github(
  project_id int not null,
  foreign key(project_id) references scaladex.Projects(id),
  readme varchar(1048576),
  description varchar(1024),
  homepage varchar(1024),
  logo varchar(1024),
  stars int,
  forks int,
  watchers int,
  issues int,
  commits int
);

create table scaladex.Contributors(
  id identity primary key,
  login varchar(255) not null,
  avatar varchar(255) not null,
  url varchar(255) not null
);

create table scaladex.Projects_Contributors(
  project_id int not null,
  foreign key(project_id) references scaladex.Projects(id),
  contributor_id int not null,
  foreign key(contributor_id) references scaladex.Contributors(id),
  primary key(project_id, contributor_id),
  commits int not null
);