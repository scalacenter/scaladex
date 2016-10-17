create table scaladex.Scala_Dependencies(
  release1_id int not null,
  foreign key(release1_id) references scaladex.Releases(id),
  release2_id int not null,
  foreign key(release2_id) references scaladex.Releases(id),
  primary key(release1_id, release2_id),
  scope varchar(255)
);

create table scaladex.Java_Dependencies(
  id int not null,
  foreign key(id) references scaladex.Releases(id),
  primary key(id),
  scope varchar(255),
  groupId varchar(1024) not null,
  artifactId varchar(1024) not null,
  version varchar(1024) not null
);