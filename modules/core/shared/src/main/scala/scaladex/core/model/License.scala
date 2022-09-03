package scaladex.core.model

/**
 * Licence representation
 *
 * @param name the licence name
 * @param shortName the short name
 * @param url the url for further reading
 */
case class License(name: String, shortName: String, url: Option[String])

object License {
  def get(shortName: String): Option[License] = allByShortName.get(shortName)

  /** inspired by: https://github.com/NixOS/nixpkgs/blob/master/lib/licenses.nix#L1 */
  val Academic: License = spdx("AFL-3.0", "Academic Free License")
  val Affero: License = spdx("AGPL-3.0", "GNU Affero General Public License v3.0")
  val Apache2: License = spdx("Apache-2.0", "Apache License 2.0")
  val Apple2_0: License = spdx("APSL-2.0", "Apple Public Source License 2.0")
  val Beerware: License = spdx("Beerware", "Beerware License")
  val Bsd2Clause: License = spdx("BSD-2-Clause", """BSD 2-clause "Simplified" License""")
  val Bsd3Clause: License = spdx("BSD-3-Clause", """BSD 3-clause "New" or "Revised" License""")
  val BsdOriginal: License = spdx("BSD-4-Clause", """BSD 4-clause "Original" or "Old" License""")
  val CreativeCommonsZeroUniversal: License = spdx("CC0-1.0", "Creative Commons Zero v1.0 Universal")
  val CreativeCommonsAttributionNonCommercialShareAlike_2_0: License =
    spdx(
      "CC-BY-NC-SA-2.0",
      "Creative Commons Attribution Non Commercial Share Alike 2.0"
    )
  val CreativeCommonsAttributionNonCommercialShareAlike_2_5: License =
    spdx(
      "CC-BY-NC-SA-2.5",
      "Creative Commons Attribution Non Commercial Share Alike 2.5"
    )
  val CreativeCommonsAttributionNonCommercialShareAlike_3_0: License =
    spdx(
      "CC-BY-NC-SA-3.0",
      "Creative Commons Attribution Non Commercial Share Alike 3.0"
    )
  val CreativeCommonsAttributionNonCommercialShareAlike_4_0: License =
    spdx(
      "CC-BY-NC-SA-4.0",
      "Creative Commons Attribution Non Commercial Share Alike 4.0"
    )
  val CreativeCommonsAttributionShareAlike_2_5: License =
    spdx("CC-BY-SA-2.5", "Creative Commons Attribution Share Alike 2.5")
  val CreativeCommonsAttribution_3_0: License = spdx("CC-BY-3.0", "Creative Commons Attribution 3.0")
  val CreativeCommonsAttributionShareAlike_3_0: License =
    spdx("CC-BY-SA-3.0", "Creative Commons Attribution Share Alike 3.0")
  val CreativeCommonsAttribution_4_0: License = spdx("CC-BY-4.0", "Creative Commons Attribution 4.0")
  val CreativeCommonsAttributionShareAlike_4_0: License =
    spdx("CC-BY-SA-4.0", "Creative Commons Attribution Share Alike 4.0")
  val Eclipse: License = spdx("EPL-1.0", "Eclipse Public License 1.0")
  val GPL1: License = spdx("GPL-1.0", "GNU General Public License v1.0 only")
  val GPL1Plus: License = spdx("GPL-1.0+", "GNU General Public License v1.0 or later")
  val GPL2: License = spdx("GPL-2.0", "GNU General Public License v2.0 only")
  val GPL2Plus: License = spdx("GPL-2.0+", "GNU General Public License v2.0 or later")
  val GPL3: License = spdx("GPL-3.0", "GNU General Public License v3.0 only")
  val GPL3Plus: License = spdx("GPL-3.0+", "GNU General Public License v3.0 or later")
  val ISC: License = spdx("ISC", "ISC License")
  val LGPL2: License = spdx("LGPL-2.0", "GNU Library General Public License v2 only")
  // @deprecated("-", "-")
  val LGPL2_Plus: License = spdx("LGPL-2.0+", "GNU Library General Public License v2 or later")
  val LGPL2_1: License = spdx("LGPL-2.1", "GNU Library General Public License v2.1 only")
  // @deprecated("-", "-")
  val LGPL2_1_Plus: License = spdx("LGPL-2.1+", "GNU Library General Public License v2.1 or later")
  val LGPL3: License = spdx("LGPL-3.0", "GNU Lesser General Public License v3.0 only")
  // @deprecated("use LGPL3", "2.0rc2")
  val LGPL3_Plus: License = spdx("LGPL-3.0+", "GNU Lesser General Public License v3.0 or later")
  // Spdx.org does not (yet) differentiate between the X11 and Expat versions
  // for details see http://en.wikipedia.org/wiki/MIT_License#Various_versions
  val MIT: License = spdx("MIT", "MIT License")
  val MPL_1_0: License = spdx("MPL-1.0", "Mozilla Public License 1.0")
  val MPL_1_1: License = spdx("MPL-1.1", "Mozilla Public License 1.1")
  val MPL2: License = spdx("MPL-2.0", "Mozilla Public License 2.0")
  val PublicDomain: License = License("Public Domain", "Public Domain", None)
  val Scala: License =
    License(
      "Scala License",
      "Scala License",
      Some("http://www.scala-lang.org/license.html")
    )
  val TypesafeSubscriptionAgreement: License = License(
    "Typesafe Subscription Agreement",
    "Typesafe Subscription Agreement",
    Some(
      "http://downloads.typesafe.com/website/legal/TypesafeSubscriptionAgreement.pdf"
    )
  )
  val Unlicense: License = spdx("Unlicense", "The Unlicense")
  val W3C: License = spdx("W3C", "W3C Software Notice and License")
  val WTFPL: License = spdx("WTFPL", "Do What The F*ck You Want To Public License")

  val allByShortName: Map[String, License] = Map(
    Academic.shortName -> Academic,
    "Academic License (for original lex files)" -> Academic,
    Affero.shortName -> Affero,
    "Affero GPLv3" -> Affero,
    "AGPL licencse" -> Affero,
    "AGPL" -> Affero,
    "AGPL v3" -> Affero,
    "AGPLv3" -> Affero,
    Apache2.shortName -> Apache2,
    "Apache 2" -> Apache2,
    "Apache License, Version 2.0" -> Apache2,
    "Apache 2.0" -> Apache2,
    "The Apache Software License, Version 2.0" -> Apache2,
    "Apache V2" -> Apache2,
    "Apache2" -> Apache2,
    "Apache 2.0 License" -> Apache2,
    "Apache License 2.0" -> Apache2,
    "the Apache License, ASL Version 2.0" -> Apache2,
    "Apache License" -> Apache2,
    "The Apache License, Version 2.0" -> Apache2,
    "Apache" -> Apache2,
    "Apache-style" -> Apache2,
    "Apache License, ASL Version 2.0" -> Apache2,
    "Apache v2" -> Apache2,
    "Apache License Version 2.0" -> Apache2,
    "Apache license" -> Apache2,
    "Apache License, Verison 2.0" -> Apache2,
    "Apache 2.0 (for supplemental code)" -> Apache2,
    "APACHE-2.0" -> Apache2,
    "The Apache Software Licence, Version 2.0" -> Apache2,
    "Apache 2 License" -> Apache2,
    "The Apache License, ASL Version 2.0" -> Apache2,
    "Apache License, Verision 2.0" -> Apache2,
    "Apache Public License 2.0" -> Apache2,
    "Apache License v2" -> Apache2,
    "Apache Software License Version 2.0" -> Apache2,
    "Apache 2 license" -> Apache2,
    "Apache License, Version 2" -> Apache2,
    "Apache Software License 2.0" -> Apache2,
    "ASL" -> Apache2,
    "ASF License" -> Apache2,
    Apple2_0.shortName -> Apple2_0,
    Beerware.shortName -> Beerware,
    Bsd2Clause.shortName -> Bsd2Clause,
    "BSD Simplified" -> Bsd2Clause,
    "Simplified BSD License" -> Bsd2Clause,
    "The BSD 2-Clause License" -> Bsd2Clause,
    "BSD Software License, 2-clause version" -> Bsd2Clause,
    "Two-clause BSD-style license" -> Bsd2Clause,
    "BSD 2 clause" -> Bsd2Clause,
    "BSD 2 Clause" -> Bsd2Clause,
    "BSD-2" -> Bsd2Clause,
    Bsd3Clause.shortName -> Bsd3Clause,
    "BSD New" -> Bsd3Clause,
    "New BSD" -> Bsd3Clause,
    "New BSD License" -> Bsd3Clause,
    "The New BSD License" -> Bsd3Clause,
    "Three-clause BSD-style" -> Bsd3Clause,
    "BSD 3-Clause" -> Bsd3Clause,
    "BSD 3-clause" -> Bsd3Clause,
    "The BSD 3-Clause License" -> Bsd3Clause,
    "BSD 3-Clause License" -> Bsd3Clause,
    "3-clause BSD" -> Bsd3Clause,
    "BSD3" -> Bsd3Clause,
    "BSD-style" -> Bsd3Clause,
    "BSD" -> Bsd3Clause,
    "BSD-like" -> Bsd3Clause,
    "BSD-Style" -> Bsd3Clause,
    "modified BSD License" -> Bsd3Clause,
    BsdOriginal.shortName -> BsdOriginal,
    CreativeCommonsZeroUniversal.shortName -> CreativeCommonsZeroUniversal,
    "CC0" -> CreativeCommonsZeroUniversal,
    CreativeCommonsAttributionNonCommercialShareAlike_2_0.shortName -> CreativeCommonsAttributionNonCommercialShareAlike_2_0,
    CreativeCommonsAttributionNonCommercialShareAlike_2_5.shortName -> CreativeCommonsAttributionNonCommercialShareAlike_2_5,
    CreativeCommonsAttributionNonCommercialShareAlike_3_0.shortName -> CreativeCommonsAttributionNonCommercialShareAlike_3_0,
    CreativeCommonsAttributionNonCommercialShareAlike_4_0.shortName -> CreativeCommonsAttributionNonCommercialShareAlike_4_0,
    CreativeCommonsAttributionShareAlike_2_5.shortName -> CreativeCommonsAttributionShareAlike_2_5,
    CreativeCommonsAttribution_3_0.shortName -> CreativeCommonsAttribution_3_0,
    CreativeCommonsAttributionShareAlike_3_0.shortName -> CreativeCommonsAttributionShareAlike_3_0,
    CreativeCommonsAttribution_4_0.shortName -> CreativeCommonsAttribution_4_0,
    "CC BY 4.0." -> CreativeCommonsAttribution_4_0,
    CreativeCommonsAttributionShareAlike_4_0.shortName -> CreativeCommonsAttributionShareAlike_4_0,
    Eclipse.shortName -> Eclipse,
    "Eclipse Public License, Version 1.0" -> Eclipse,
    "Eclipse Public License v1.0" -> Eclipse,
    "Eclipse Public License - v 1.0" -> Eclipse,
    "EPL" -> Eclipse,
    GPL1.shortName -> GPL1,
    GPL1Plus.shortName -> GPL1Plus,
    GPL2.shortName -> GPL2,
    "Dual-licensed under the GNU General Public License, version 2.0 (GPLv2) and the Geeoz Commercial License." -> GPL2,
    "GNU General Public License, version 2 (GPL-2.0)" -> GPL2,
    "GNU General Public License v2" -> GPL2,
    "GNU General Public License, version 2" -> GPL2,
    GPL2Plus.shortName -> GPL2Plus,
    "GPL v2+" -> GPL2Plus,
    GPL3.shortName -> GPL3,
    "GPL-3.0" -> GPL3,
    "GPLv3" -> GPL3,
    "GNU GPL" -> GPL3,
    "GNU GPL v3" -> GPL3,
    "GPL v3" -> GPL3,
    "GNU General Public License (GPL)" -> GPL3,
    "GNU Gpl v3" -> GPL3,
    "GPL" -> GPL3,
    "GNU GENERAL PUBLIC LICENSE" -> GPL3,
    "GNU General Public License, Version 3" -> GPL3,
    "GNU GENERAL PUBLIC LICENSE, Version 3.0" -> GPL3,
    GPL3Plus.shortName -> GPL3Plus,
    "GPL v3+" -> GPL3Plus,
    "GPL version 3 or any later version" -> GPL3Plus,
    "GNU General Public License v3+" -> GPL3Plus,
    ISC.shortName -> ISC,
    "ISC License" -> ISC,
    LGPL2.shortName -> LGPL2,
    LGPL2_Plus.shortName -> LGPL2_Plus,
    LGPL2_1.shortName -> LGPL2_1,
    "LGPLv2.1" -> LGPL2_1,
    "LGPL 2.1" -> LGPL2_1,
    "GNU Lesser General Public License, Version 2.1" -> LGPL2_1,
    LGPL2_1_Plus.shortName -> LGPL2_1_Plus,
    "LGPL v2.1+" -> LGPL2_1_Plus,
    LGPL3.shortName -> LGPL3,
    "LGPL 3.0 license" -> LGPL3,
    "LGPLv3" -> LGPL3,
    "LGPL 3.0" -> LGPL3,
    "LGPL3" -> LGPL3,
    "LGPL v3" -> LGPL3,
    "LGPL" -> LGPL3,
    "GNU Library or Lesser General Public License (LGPL)" -> LGPL3,
    "GNU LGPL" -> LGPL3,
    "lgpl" -> LGPL3,
    "GNU Lesser General Public License" -> LGPL3,
    "GNU Lesser General Public License v3.0" -> LGPL3,
    "GNU Lesser General Public Licence" -> LGPL3,
    "GNU LESSER GENERAL PUBLIC LICENSE, Version 3" -> LGPL3,
    LGPL3_Plus.shortName -> LGPL3_Plus,
    "LGPL v3+" -> LGPL3_Plus,
    MIT.shortName -> MIT,
    "MIT License" -> MIT,
    "MIT-style" -> MIT,
    "MIT license" -> MIT,
    "The MIT License" -> MIT,
    "The MIT License (MIT)" -> MIT,
    "MIT licencse" -> MIT,
    "mit" -> MIT,
    "MIT Licence" -> MIT,
    "$MIT" -> MIT,
    "MIT-style license" -> MIT,
    "AThe MIT License (MIT)" -> MIT,
    MPL_1_0.shortName -> MPL_1_0,
    MPL_1_1.shortName -> MPL_1_1,
    MPL2.shortName -> MPL2,
    "MPL 2.0" -> MPL2,
    "Mozilla Public License Version 2.0" -> MPL2,
    "Mozilla Public License, version 2.0" -> MPL2,
    "Mozilla Public License" -> MPL2,
    PublicDomain.shortName -> PublicDomain,
    "Public domain" -> PublicDomain,
    Scala.shortName -> Scala,
    "Scala license" -> Scala,
    TypesafeSubscriptionAgreement.shortName -> TypesafeSubscriptionAgreement,
    Unlicense.shortName -> Unlicense,
    "The Unlicense" -> Unlicense,
    "unlicense" -> Unlicense,
    W3C.shortName -> W3C,
    WTFPL.shortName -> WTFPL,
    "WTFPL, Version 2" -> WTFPL
  )

  private def spdx(shortName: String, name: String): License =
    License(name, shortName, Some(s"https://spdx.org/licenses/$shortName.html"))
}
