package scaladex.data.bintray

/**
 * General information about a Bintray package as defined by the Bintray REST API
 * https://bintray.com/docs/api/#_get_package
 */
case class BintrayPackage(
    name: String,
    updated: String,
    owner: String,
    repo: String,
    vcs_url: String
)
