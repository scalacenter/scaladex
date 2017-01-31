package ch.epfl.scala.index.model

/**
  * Licence representation
  * @param name the licence name
  * @param shortName the short name
  * @param url the url for further reading
  */
case class License(name: String, shortName: String, url: Option[String])

object License {

  def spdx(id: String, name: String) =
    License(name, id, Some(s"https://spdx.org/licenses/$id.html"))

  /** inspired by: https://github.com/NixOS/nixpkgs/blob/master/lib/licenses.nix#L1 */
  val Academic = spdx("AFL-3.0", "Academic Free License")
  val Affero = spdx("AGPL-3.0", "GNU Affero General Public License v3.0")
  val Apache2 = spdx("Apache-2.0", "Apache License 2.0")
  val Apple2_0 = spdx("APSL-2.0", "Apple Public Source License 2.0")
  val Beerware = spdx("Beerware", "Beerware License")
  val Bsd2Clause = spdx("BSD-2-Clause", """BSD 2-clause "Simplified" License""")
  val Bsd3Clause = spdx("BSD-3-Clause", """BSD 3-clause "New" or "Revised" License""")
  val BsdOriginal = spdx("BSD-4-Clause", """BSD 4-clause "Original" or "Old" License""")
  val CreativeCommonsZeroUniversal = spdx("CC0-1.0", "Creative Commons Zero v1.0 Universal")
  val CreativeCommonsAttributionNonCommercialShareAlike_2_0 =
    spdx("CC-BY-NC-SA-2.0", "Creative Commons Attribution Non Commercial Share Alike 2.0")
  val CreativeCommonsAttributionNonCommercialShareAlike_2_5 =
    spdx("CC-BY-NC-SA-2.5", "Creative Commons Attribution Non Commercial Share Alike 2.5")
  val CreativeCommonsAttributionNonCommercialShareAlike_3_0 =
    spdx("CC-BY-NC-SA-3.0", "Creative Commons Attribution Non Commercial Share Alike 3.0")
  val CreativeCommonsAttributionNonCommercialShareAlike_4_0 =
    spdx("CC-BY-NC-SA-4.0", "Creative Commons Attribution Non Commercial Share Alike 4.0")
  val CreativeCommonsAttributionShareAlike_2_5 =
    spdx("CC-BY-SA-2.5", "Creative Commons Attribution Share Alike 2.5")
  val CreativeCommonsAttribution_3_0 = spdx("CC-BY-3.0", "Creative Commons Attribution 3.0")
  val CreativeCommonsAttributionShareAlike_3_0 =
    spdx("CC-BY-SA-3.0", "Creative Commons Attribution Share Alike 3.0")
  val CreativeCommonsAttribution_4_0 = spdx("CC-BY-4.0", "Creative Commons Attribution 4.0")
  val CreativeCommonsAttributionShareAlike_4_0 =
    spdx("CC-BY-SA-4.0", "Creative Commons Attribution Share Alike 4.0")
  val Eclipse = spdx("EPL-1.0", "Eclipse Public License 1.0")
  val GPL1 = spdx("GPL-1.0", "GNU General Public License v1.0 only")
  val GPL1Plus = spdx("GPL-1.0+", "GNU General Public License v1.0 or later")
  val GPL2 = spdx("GPL-2.0", "GNU General Public License v2.0 only")
  val GPL2Plus = spdx("GPL-2.0+", "GNU General Public License v2.0 or later")
  val GPl3 = spdx("GPL-3.0", "GNU General Public License v3.0 only")
  val GPL3Plus = spdx("GPL-3.0+", "GNU General Public License v3.0 or later")
  val ISC = spdx("ISC", "ISC License")
  val LGPL2 = spdx("LGPL-2.0", "GNU Library General Public License v2 only")
  // @deprecated("-", "-")
  val LGPL2_Plus = spdx("LGPL-2.0+", "GNU Library General Public License v2 or later")
  val LGPL2_1 = spdx("LGPL-2.1", "GNU Library General Public License v2.1 only")
  // @deprecated("-", "-")
  val LGPL2_1_Plus = spdx("LGPL-2.1+", "GNU Library General Public License v2.1 or later")
  val LGPL3 = spdx("LGPL-3.0", "GNU Lesser General Public License v3.0 only")
  // @deprecated("use LGPL3", "2.0rc2")
  val LGPL3_Plus = spdx("LGPL-3.0+", "GNU Lesser General Public License v3.0 or later")
  // Spdx.org does not (yet) differentiate between the X11 and Expat versions
  // for details see http://en.wikipedia.org/wiki/MIT_License#Various_versions
  val MIT = spdx("MIT", "MIT License")
  val MPL_1_0 = spdx("MPL-1.0", "Mozilla Public License 1.0")
  val MPL_1_1 = spdx("MPL-1.1", "Mozilla Public License 1.1")
  val MPL2 = spdx("MPL-2.0", "Mozilla Public License 2.0")
  val PublicDomain = License("Public Domain", "Public Domain", None)
  val Scala =
    License("Scala License", "Scala License", Some("http://www.scala-lang.org/license.html"))
  val TypesafeSubscriptionAgreement = License(
    "Typesafe Subscription Agreement",
    "Typesafe Subscription Agreement",
    Some("http://downloads.typesafe.com/website/legal/TypesafeSubscriptionAgreement.pdf")
  )
  val Unlicense = spdx("Unlicense", "The Unlicense")
  val W3C = spdx("W3C", "W3C Software Notice and License")
  val WTFPL = spdx("WTFPL", "Do What The F*ck You Want To Public License")

  /**
    * list of all licences
    * @return
    */
  def all = List(
    Academic,
    Affero,
    Apache2,
    Apple2_0,
    Beerware,
    Bsd2Clause,
    Bsd3Clause,
    BsdOriginal,
    CreativeCommonsZeroUniversal,
    CreativeCommonsAttributionNonCommercialShareAlike_2_0,
    CreativeCommonsAttributionNonCommercialShareAlike_2_5,
    CreativeCommonsAttributionNonCommercialShareAlike_3_0,
    CreativeCommonsAttributionNonCommercialShareAlike_4_0,
    CreativeCommonsAttributionShareAlike_2_5,
    CreativeCommonsAttribution_3_0,
    CreativeCommonsAttributionShareAlike_3_0,
    CreativeCommonsAttribution_4_0,
    CreativeCommonsAttributionShareAlike_4_0,
    Eclipse,
    GPL1,
    GPL1Plus,
    GPL2,
    GPL2Plus,
    GPl3,
    GPL3Plus,
    ISC,
    LGPL2,
    LGPL2_Plus,
    LGPL2_1,
    LGPL2_1_Plus,
    LGPL3,
    LGPL3_Plus,
    MIT,
    MPL_1_0,
    MPL_1_1,
    MPL2,
    PublicDomain,
    Scala,
    TypesafeSubscriptionAgreement,
    Unlicense,
    W3C,
    WTFPL
  )
}
