package scaladex.core.model

/**
 * Contributor reference
 *
 * @param login         the user name
 * @param avatarUrl     the url to the users avatar
 * @param contributions the number of contributions
 */
case class GithubContributor(
    login: String,
    avatarUrl: String,
    url: Url,
    contributions: Int
) extends AvatarUrl
