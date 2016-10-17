create schema if not exists scaladex;

create table scaladex.Projects(
  id identity primary key,
  organization varchar(255) not null,
  repository varchar(255) not null,
  default_artifact varchar(255) not null,
  default_stable_version boolean not null,
  custom_scala_doc varchar(255),
  deprecated boolean not null,
  test boolean not null,
  contributors_wanted boolean not null,
  live_data boolean not null,
);  