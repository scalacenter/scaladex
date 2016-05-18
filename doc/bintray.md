# Bintray Api

## Authentication
create a Bintray account
get your key here:
https://bintray.com/profile/edit
in tab API Key

it look like:
xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx (sha256)
or
xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx (sha1)
this is the _apikey_

your _username_ is also part of the authentication

# File search

https://bintray.com/docs/api/#_file_search_by_name

curl -u _username_:_apikey_
  https://bintray.com/api/v1/search/file
  ?name=*_2.11*.pom
  &start_pos=0
  &created_after=2015-08-27T10:23:50.575Z