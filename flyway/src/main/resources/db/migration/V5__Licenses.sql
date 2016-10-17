create table scaladex.Licenses(
  id integer not null primary key,
  name varchar(1024) not null,
  shortName varchar(1024) not null,
  url varchar(1024)
);

create table scaladex.Releases_Licenses(
  release_id int not null,
  foreign key(release_id) references scaladex.Releases(id),
  license_id int not null,
  foreign key(license_id) references scaladex.Licenses(id),
  primary key(release_id, license_id)
);