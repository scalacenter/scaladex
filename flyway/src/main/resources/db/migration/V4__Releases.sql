create table scaladex.Releases(
  id integer not null primary key,
  groupId varchar(1024) not null,
  artifactId varchar(1024) not null,
  version varchar(1024) not null,
  non_standard_lib boolean not null,
  live_data boolean not null,
  released date not null,
  test boolean not null,
  bintray_owner varchar(1024) not null,
  bintray_repo varchar(1024) not null
);