# Github API

## Authentication
create token here: https://github.com/settings/tokens/new

Example:

curl \
  -H "Authorization: token xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \
  https://api.github.com/repos/MasseGuillaume/ScalaKata2/readme

## README API

* [doc](https://developer.github.com/v3/repos/contents/#get-the-readme)

Example:

`curl https://api.github.com/repos/MasseGuillaume/ScalaKata2/readme`

```json
{
  "name": "README.md",
  "path": "README.md",
  "sha": "65922f04ee33d69ebf3cc1100c033a9297beeffe",
  "size": 1029,
  "url": "https://api.github.com/repos/MasseGuillaume/ScalaKata2/contents/README.md?ref=master",
  "html_url": "https://github.com/MasseGuillaume/ScalaKata2/blob/master/README.md",
  "git_url": "https://api.github.com/repos/MasseGuillaume/ScalaKata2/git/blobs/65922f04ee33d69ebf3cc1100c033a9297beeffe",
  "download_url": "https://raw.githubusercontent.com/MasseGuillaume/ScalaKata2/master/README.md",
  "type": "file",
  "content": "IyBTY2FsYUthdGEKClshW1N0b3JpZXMgaW4gUmVhZHldKGh0dHBzOi8vaW1n\nLnNoaWVsZHMuaW8vd2FmZmxlL2xhYmVsL01hc3NlR3VpbGxhdW1lL1NjYWxh\nS2F0YTIuc3ZnP3N0eWxlPWZsYXQtc3F1YXJlKV0oaHR0cHM6Ly93YWZmbGUu\naW8vTWFzc2VHdWlsbGF1bWUvU2NhbGFLYXRhMikgClshW1RyYXZpcyBCdWls\nZCBTdGF0dXNdKGh0dHBzOi8vaW1nLnNoaWVsZHMuaW8vdHJhdmlzL01hc3Nl\nR3VpbGxhdW1lL1NjYWxhS2F0YTIuc3ZnP3N0eWxlPWZsYXQtc3F1YXJlKV0o\naHR0cHM6Ly90cmF2aXMtY2kub3JnL01hc3NlR3VpbGxhdW1lL1NjYWxhS2F0\nYTIpIApbIVtXaW5kb3dzIEJ1aWxkIHN0YXR1c10oaHR0cHM6Ly9pbWcuc2hp\nZWxkcy5pby9hcHB2ZXlvci9jaS9NYXNzZUd1aWxsYXVtZS9TY2FsYUthdGEy\nLnN2Zz9zdHlsZT1mbGF0LXNxdWFyZSldKGh0dHBzOi8vY2kuYXBwdmV5b3Iu\nY29tL3Byb2plY3QvTWFzc2VHdWlsbGF1bWUvc2NhbGFrYXRhMi9icmFuY2gv\nbWFzdGVyKSAKWyFbQ2hhdCBvbiBHaXR0ZXJdKGh0dHBzOi8vYmFkZ2VzLmdp\ndHRlci5pbS9Kb2luJTIwQ2hhdC5zdmcpXShodHRwczovL2dpdHRlci5pbS9N\nYXNzZUd1aWxsYXVtZS9TY2FsYUthdGEyKSAKCiFbRGVtb10oL21pc2MvZGVt\nby5naWYpCgojIyBEaXN0cmlidXRpb25zCgojIyMgU2J0IFBsdWdpbgoKQWRk\nIHRoZSBmb2xsb3dpbmcgbGluZSB0byBgcHJvamVjdC9wbHVnaW5zLnNidGAK\nCmBgYHNjYWxhCmFkZFNidFBsdWdpbigiY29tLnNjYWxha2F0YSIgJSAic2J0\nLXNjYWxha2F0YSIgJSAiMS4xLjMiKQpgYGAKCkFuZCBhZGQgdGhlIGZvbGxv\nd2luZyBsaW5lIHRvIGBidWlsZC5zYnRgCgpgYGBzY2FsYQplbmFibGVQbHVn\naW5zKFNjYWxhS2F0YVBsdWdpbikKYGBgCgojIyMgRG9ja2VyIGNvbnRhaW5l\ncgoKYHN1ZG8gZG9ja2VyIHJ1biAtcCA3MzMxOjczMzEgLS1uYW1lIHNjYWxh\na2F0YSBtYXNzZWd1aWxsYXVtZS9zY2FsYWthdGE6djEuMS4zYAoKb3BlbiB5\nb3VyIGJyb3dzZXIgYXQgYGh0dHA6Ly9sb2NhbGhvc3Q6NzMzMWAK\n",
  "encoding": "base64",
  "_links": {
    "self": "https://api.github.com/repos/MasseGuillaume/ScalaKata2/contents/README.md?ref=master",
    "git": "https://api.github.com/repos/MasseGuillaume/ScalaKata2/git/blobs/65922f04ee33d69ebf3cc1100c033a9297beeffe",
    "html": "https://github.com/MasseGuillaume/ScalaKata2/blob/master/README.md"
  }
}
```

```scala
import java.util.Base64
import java.nio.charset.StandardCharsets
val content = "IyBTY2FsYUthdGEKClshW1N0b3JpZXMgaW4gUmVhZHldKGh0dHBzOi8vaW1n\nLnNoaWVsZHMuaW8vd2FmZmxlL2xhYmVsL01hc3NlR3VpbGxhdW1lL1NjYWxh\nS2F0YTIuc3ZnP3N0eWxlPWZsYXQtc3F1YXJlKV0oaHR0cHM6Ly93YWZmbGUu\naW8vTWFzc2VHdWlsbGF1bWUvU2NhbGFLYXRhMikgClshW1RyYXZpcyBCdWls\nZCBTdGF0dXNdKGh0dHBzOi8vaW1nLnNoaWVsZHMuaW8vdHJhdmlzL01hc3Nl\nR3VpbGxhdW1lL1NjYWxhS2F0YTIuc3ZnP3N0eWxlPWZsYXQtc3F1YXJlKV0o\naHR0cHM6Ly90cmF2aXMtY2kub3JnL01hc3NlR3VpbGxhdW1lL1NjYWxhS2F0\nYTIpIApbIVtXaW5kb3dzIEJ1aWxkIHN0YXR1c10oaHR0cHM6Ly9pbWcuc2hp\nZWxkcy5pby9hcHB2ZXlvci9jaS9NYXNzZUd1aWxsYXVtZS9TY2FsYUthdGEy\nLnN2Zz9zdHlsZT1mbGF0LXNxdWFyZSldKGh0dHBzOi8vY2kuYXBwdmV5b3Iu\nY29tL3Byb2plY3QvTWFzc2VHdWlsbGF1bWUvc2NhbGFrYXRhMi9icmFuY2gv\nbWFzdGVyKSAKWyFbQ2hhdCBvbiBHaXR0ZXJdKGh0dHBzOi8vYmFkZ2VzLmdp\ndHRlci5pbS9Kb2luJTIwQ2hhdC5zdmcpXShodHRwczovL2dpdHRlci5pbS9N\nYXNzZUd1aWxsYXVtZS9TY2FsYUthdGEyKSAKCiFbRGVtb10oL21pc2MvZGVt\nby5naWYpCgojIyBEaXN0cmlidXRpb25zCgojIyMgU2J0IFBsdWdpbgoKQWRk\nIHRoZSBmb2xsb3dpbmcgbGluZSB0byBgcHJvamVjdC9wbHVnaW5zLnNidGAK\nCmBgYHNjYWxhCmFkZFNidFBsdWdpbigiY29tLnNjYWxha2F0YSIgJSAic2J0\nLXNjYWxha2F0YSIgJSAiMS4xLjMiKQpgYGAKCkFuZCBhZGQgdGhlIGZvbGxv\nd2luZyBsaW5lIHRvIGBidWlsZC5zYnRgCgpgYGBzY2FsYQplbmFibGVQbHVn\naW5zKFNjYWxhS2F0YVBsdWdpbikKYGBgCgojIyMgRG9ja2VyIGNvbnRhaW5l\ncgoKYHN1ZG8gZG9ja2VyIHJ1biAtcCA3MzMxOjczMzEgLS1uYW1lIHNjYWxh\na2F0YSBtYXNzZWd1aWxsYXVtZS9zY2FsYWthdGE6djEuMS4zYAoKb3BlbiB5\nb3VyIGJyb3dzZXIgYXQgYGh0dHA6Ly9sb2NhbGhvc3Q6NzMzMWAK\n".
  replaceAll("\n", "")

new String(Base64.getDecoder.decode(content), "UTF-8")
```