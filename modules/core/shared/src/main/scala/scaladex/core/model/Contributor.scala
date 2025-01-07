package scaladex.core.model

/** Description of a person who has contributed to the project, but who does not have commit privileges. Usually, these
  * contributions come in the form of patches submitted.
  */
case class Contributor(
    name: Option[String],
    email: Option[String],
    url: Option[String],
    organization: Option[String],
    organizationUrl: Option[String],
    roles: List[String],
    /*
      The timezone the contributor is in. Typically, this is a number in the range
      <a href="http://en.wikipedia.org/wiki/UTC%E2%88%9212:00">-12</a> to <a href="http://en.wikipedia.org/wiki/UTC%2B14:00">+14</a>
      or a valid time zone id like "America/Montreal" (UTC-05:00) or "Europe/Paris" (UTC+01:00).
     */
    timezone: Option[String],
    // Properties about the contributor, such as an instant messenger handle.
    properties: Map[String, String],
    // Developer
    // The unique ID of the developer in the SCM.
    id: Option[String]
)
