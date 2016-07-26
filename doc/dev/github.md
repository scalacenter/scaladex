# Github API

## Authentication
create token here: https://github.com/settings/tokens/new

## Repo

### Info

[doc](https://developer.github.com/v3/repos/#get)

`curl https://api.github.com/repos/typelevel/cats`

### README API

[doc](https://developer.github.com/v3/repos/contents/#get-the-readme)

Example:

```
curl \
  -H "Accept: application/vnd.github.VERSION.html" \
  -H "Authorization: token xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \
  https://api.github.com/repos/MasseGuillaume/ScalaKata2/readme
```

### JQ

[jq](https://stedolan.github.io/jq/) is a lightweight and flexible command-line JSON processor. 

curl \
  -H "Authorization: token xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \
  https://api.github.com/orgs/masseguillaume/repos | jq '.[] | {name: .full_name, per: .permissions}'
