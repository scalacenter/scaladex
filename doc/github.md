# Github API

## Authentication
create token here: https://github.com/settings/tokens/new

Example:

curl \
  -H "Authorization: token xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \
  https://api.github.com/repos/MasseGuillaume/ScalaKata2/readme


## Repo

### Info

[doc](https://developer.github.com/v3/repos/#get)

`curl https://api.github.com/repos/typelevel/cats`

{
  "id": 29986727,
  "name": "cats",
  "full_name": "typelevel/cats",
  "owner": {
    "login": "typelevel",
    "id": 3731824,
    "avatar_url": "https://avatars.githubusercontent.com/u/3731824?v=3",
    "gravatar_id": "",
    "url": "https://api.github.com/users/typelevel",
    "html_url": "https://github.com/typelevel",
    "followers_url": "https://api.github.com/users/typelevel/followers",
    "following_url": "https://api.github.com/users/typelevel/following{/other_user}",
    "gists_url": "https://api.github.com/users/typelevel/gists{/gist_id}",
    "starred_url": "https://api.github.com/users/typelevel/starred{/owner}{/repo}",
    "subscriptions_url": "https://api.github.com/users/typelevel/subscriptions",
    "organizations_url": "https://api.github.com/users/typelevel/orgs",
    "repos_url": "https://api.github.com/users/typelevel/repos",
    "events_url": "https://api.github.com/users/typelevel/events{/privacy}",
    "received_events_url": "https://api.github.com/users/typelevel/received_events",
    "type": "Organization",
    "site_admin": false
  },
  "private": false,
  "html_url": "https://github.com/typelevel/cats",
  "description": "Lightweight, modular, and extensible library for functional programming.",
  "fork": false,
  "url": "https://api.github.com/repos/typelevel/cats",
  "forks_url": "https://api.github.com/repos/typelevel/cats/forks",
  "keys_url": "https://api.github.com/repos/typelevel/cats/keys{/key_id}",
  "collaborators_url": "https://api.github.com/repos/typelevel/cats/collaborators{/collaborator}",
  "teams_url": "https://api.github.com/repos/typelevel/cats/teams",
  "hooks_url": "https://api.github.com/repos/typelevel/cats/hooks",
  "issue_events_url": "https://api.github.com/repos/typelevel/cats/issues/events{/number}",
  "events_url": "https://api.github.com/repos/typelevel/cats/events",
  "assignees_url": "https://api.github.com/repos/typelevel/cats/assignees{/user}",
  "branches_url": "https://api.github.com/repos/typelevel/cats/branches{/branch}",
  "tags_url": "https://api.github.com/repos/typelevel/cats/tags",
  "blobs_url": "https://api.github.com/repos/typelevel/cats/git/blobs{/sha}",
  "git_tags_url": "https://api.github.com/repos/typelevel/cats/git/tags{/sha}",
  "git_refs_url": "https://api.github.com/repos/typelevel/cats/git/refs{/sha}",
  "trees_url": "https://api.github.com/repos/typelevel/cats/git/trees{/sha}",
  "statuses_url": "https://api.github.com/repos/typelevel/cats/statuses/{sha}",
  "languages_url": "https://api.github.com/repos/typelevel/cats/languages",
  "stargazers_url": "https://api.github.com/repos/typelevel/cats/stargazers",
  "contributors_url": "https://api.github.com/repos/typelevel/cats/contributors",
  "subscribers_url": "https://api.github.com/repos/typelevel/cats/subscribers",
  "subscription_url": "https://api.github.com/repos/typelevel/cats/subscription",
  "commits_url": "https://api.github.com/repos/typelevel/cats/commits{/sha}",
  "git_commits_url": "https://api.github.com/repos/typelevel/cats/git/commits{/sha}",
  "comments_url": "https://api.github.com/repos/typelevel/cats/comments{/number}",
  "issue_comment_url": "https://api.github.com/repos/typelevel/cats/issues/comments{/number}",
  "contents_url": "https://api.github.com/repos/typelevel/cats/contents/{+path}",
  "compare_url": "https://api.github.com/repos/typelevel/cats/compare/{base}...{head}",
  "merges_url": "https://api.github.com/repos/typelevel/cats/merges",
  "archive_url": "https://api.github.com/repos/typelevel/cats/{archive_format}{/ref}",
  "downloads_url": "https://api.github.com/repos/typelevel/cats/downloads",
  "issues_url": "https://api.github.com/repos/typelevel/cats/issues{/number}",
  "pulls_url": "https://api.github.com/repos/typelevel/cats/pulls{/number}",
  "milestones_url": "https://api.github.com/repos/typelevel/cats/milestones{/number}",
  "notifications_url": "https://api.github.com/repos/typelevel/cats/notifications{?since,all,participating}",
  "labels_url": "https://api.github.com/repos/typelevel/cats/labels{/name}",
  "releases_url": "https://api.github.com/repos/typelevel/cats/releases{/id}",
  "deployments_url": "https://api.github.com/repos/typelevel/cats/deployments",
  "created_at": "2015-01-28T20:26:48Z",
  "updated_at": "2016-06-02T13:45:39Z",
  "pushed_at": "2016-06-03T20:03:25Z",
  "git_url": "git://github.com/typelevel/cats.git",
  "ssh_url": "git@github.com:typelevel/cats.git",
  "clone_url": "https://github.com/typelevel/cats.git",
  "svn_url": "https://github.com/typelevel/cats",
  "homepage": "http://typelevel.org/cats/",
  "size": 11602,
  "stargazers_count": 1074,
  "watchers_count": 1074,
  "language": "Scala",
  "has_issues": true,
  "has_downloads": true,
  "has_wiki": true,
  "has_pages": true,
  "forks_count": 241,
  "mirror_url": null,
  "open_issues_count": 144,
  "forks": 241,
  "open_issues": 144,
  "watchers": 1074,
  "default_branch": "master",
  "organization": {
    "login": "typelevel",
    "id": 3731824,
    "avatar_url": "https://avatars.githubusercontent.com/u/3731824?v=3",
    "gravatar_id": "",
    "url": "https://api.github.com/users/typelevel",
    "html_url": "https://github.com/typelevel",
    "followers_url": "https://api.github.com/users/typelevel/followers",
    "following_url": "https://api.github.com/users/typelevel/following{/other_user}",
    "gists_url": "https://api.github.com/users/typelevel/gists{/gist_id}",
    "starred_url": "https://api.github.com/users/typelevel/starred{/owner}{/repo}",
    "subscriptions_url": "https://api.github.com/users/typelevel/subscriptions",
    "organizations_url": "https://api.github.com/users/typelevel/orgs",
    "repos_url": "https://api.github.com/users/typelevel/repos",
    "events_url": "https://api.github.com/users/typelevel/events{/privacy}",
    "received_events_url": "https://api.github.com/users/typelevel/received_events",
    "type": "Organization",
    "site_admin": false
  },
  "network_count": 241,
  "subscribers_count": 134
}

### Contributors

[doc](https://developer.github.com/v3/repos/contributors/)

```
curl \
  -H "Authorization: token xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \
  https://api.github.com//repos/typelevel/cats/contributors
```

[
  {
    "login": "ceedubs",
    "id": 977929,
    "avatar_url": "https://avatars.githubusercontent.com/u/977929?v=3",
    "gravatar_id": "",
    "url": "https://api.github.com/users/ceedubs",
    "html_url": "https://github.com/ceedubs",
    "followers_url": "https://api.github.com/users/ceedubs/followers",
    "following_url": "https://api.github.com/users/ceedubs/following{/other_user}",
    "gists_url": "https://api.github.com/users/ceedubs/gists{/gist_id}",
    "starred_url": "https://api.github.com/users/ceedubs/starred{/owner}{/repo}",
    "subscriptions_url": "https://api.github.com/users/ceedubs/subscriptions",
    "organizations_url": "https://api.github.com/users/ceedubs/orgs",
    "repos_url": "https://api.github.com/users/ceedubs/repos",
    "events_url": "https://api.github.com/users/ceedubs/events{/privacy}",
    "received_events_url": "https://api.github.com/users/ceedubs/received_events",
    "type": "User",
    "site_admin": false,
    "contributions": 409
  },
  ...
]


### README API

[doc](https://developer.github.com/v3/repos/contents/#get-the-readme)

Example:

`curl -H "Accept: application/vnd.github.VERSION.html" https://api.github.com/repos/MasseGuillaume/ScalaKata2/readme`


