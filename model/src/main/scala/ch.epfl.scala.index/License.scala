package ch.epfl.scala.index

import java.net.URL

sealed trait License {
  val name: String
  val url: Option[URL]
}

object License {
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
    GPL1_Plus,
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

case class RawLicense(
  val name: String,
  val url: Option[URL] = None
) extends License

sealed trait Spdx extends License {
  val url = Some(new URL(s"https://spdx.org/licenses/$spdxId.html"))
  def spdxId: String
}

// inspired by: https://github.com/NixOS/nixpkgs/blob/master/lib/licenses.nix#L1

case object Academic extends Spdx {
  val spdxId = "AFL-3.0"
  val name = "Academic Free License"
}

case object Affero extends Spdx {
  val spdxId = "AGPL-3.0"
  val name = "GNU Affero General Public License v3.0"
}

case object Apache2 extends Spdx {
  val spdxId = "Apache-2.0"
  val name = "Apache License 2.0"
}

case object Apple2_0 extends Spdx {
  val spdxId = "APSL-2.0"
  val name = "Apple Public Source License 2.0"
}

case object Beerware extends Spdx {
  val spdxId = "Beerware"
  val name = "Beerware License"
}

case object Bsd2Clause extends Spdx {
  val spdxId = "BSD-2-Clause"
  val name = """BSD 2-clause "Simplified" License"""
}

case object Bsd3Clause extends Spdx {
  val spdxId = "BSD-3-Clause"
  val name = """BSD 3-clause "New" or "Revised" License"""
}

case object BsdOriginal extends Spdx {
  val spdxId = "BSD-4-Clause"
  val name = """BSD 4-clause "Original" or "Old" License"""
}

case object CreativeCommonsZeroUniversal extends Spdx {
  val spdxId = "CC0-1.0"
  val name = "Creative Commons Zero v1.0 Universal"
}

case object CreativeCommonsAttributionNonCommercialShareAlike_2_0 extends Spdx {
  val spdxId = "CC-BY-NC-SA-2.0"
  val name = "Creative Commons Attribution Non Commercial Share Alike 2.0"
}

case object CreativeCommonsAttributionNonCommercialShareAlike_2_5 extends Spdx {
  val spdxId = "CC-BY-NC-SA-2.5"
  val name = "Creative Commons Attribution Non Commercial Share Alike 2.5"
}

case object CreativeCommonsAttributionNonCommercialShareAlike_3_0 extends Spdx {
  val spdxId = "CC-BY-NC-SA-3.0"
  val name = "Creative Commons Attribution Non Commercial Share Alike 3.0"
}

case object CreativeCommonsAttributionNonCommercialShareAlike_4_0 extends Spdx {
  val spdxId = "CC-BY-NC-SA-4.0"
  val name = "Creative Commons Attribution Non Commercial Share Alike 4.0"
}

case object CreativeCommonsAttributionShareAlike_2_5 extends Spdx {
  val spdxId = "CC-BY-SA-2.5"
  val name = "Creative Commons Attribution Share Alike 2.5"
}

case object CreativeCommonsAttribution_3_0 extends Spdx {
  val spdxId = "CC-BY-3.0"
  val name = "Creative Commons Attribution 3.0"
}

case object CreativeCommonsAttributionShareAlike_3_0 extends Spdx {
  val spdxId = "CC-BY-SA-3.0"
  val name = "Creative Commons Attribution Share Alike 3.0"
}

case object CreativeCommonsAttribution_4_0 extends Spdx {
  val spdxId = "CC-BY-4.0"
  val name = "Creative Commons Attribution 4.0"
}

case object CreativeCommonsAttributionShareAlike_4_0 extends Spdx {
  val spdxId = "CC-BY-SA-4.0"
  val name = "Creative Commons Attribution Share Alike 4.0"
}

case object Eclipse extends Spdx {
  val spdxId = "EPL-1.0"
  val name = "Eclipse Public License 1.0"
}

case object GPL1 extends Spdx {
  val spdxId = "GPL-1.0"
  val name = "GNU General Public License v1.0 only"
}

case object GPL1_Plus extends Spdx {
  val spdxId = "GPL-1.0+"
  val name = "GNU General Public License v1.0 or later"
}

case object GPL2 extends Spdx {
  val spdxId = "GPL-2.0"
  val name = "GNU General Public License v2.0 only"
}

case object GPL2Plus extends Spdx {
  val spdxId = "GPL-2.0+"
  val name = "GNU General Public License v2.0 or later"
}

case object GPl3 extends Spdx {
  val spdxId = "GPL-3.0"
  val name = "GNU General Public License v3.0 only"
}

case object GPL3Plus extends Spdx {
  val spdxId = "GPL-3.0+"
  val name = "GNU General Public License v3.0 or later"
}

case object ISC extends Spdx {
  val spdxId = "ISC"
  val name = "ISC License"
}

case object LGPL2 extends Spdx {
  val spdxId = "LGPL-2.0"
  val name = "GNU Library General Public License v2 only"
}

// @deprecated("-", "-")
case object LGPL2_Plus extends Spdx {
  val spdxId = "LGPL-2.0+"
  val name = "GNU Library General Public License v2 or later"
}

case object LGPL2_1 extends Spdx {
  val spdxId = "LGPL-2.1"
  val name = "GNU Library General Public License v2.1 only"
}

// @deprecated("-", "-")
case object LGPL2_1_Plus extends Spdx {
  val spdxId = "LGPL-2.1+"
  val name = "GNU Library General Public License v2.1 or later"
}

case object LGPL3 extends Spdx {
  val spdxId = "LGPL-3.0"
  val name = "GNU Lesser General Public License v3.0 only"
}

// @deprecated("use LGPL3", "2.0rc2")
case object LGPL3_Plus extends Spdx {
  val spdxId = "LGPL-3.0+"
  val name = "GNU Lesser General Public License v3.0 or later"
}

// Spdx.org does not (yet) differentiate between the X11 and Expat versions
// for details see http://en.wikipedia.org/wiki/MIT_License#Various_versions
case object MIT extends Spdx {
  val spdxId = "MIT"
  val name = "MIT License"
}

case object MPL_1_0 extends Spdx {
  val spdxId = "MPL-1.0"
  val name = "Mozilla Public License 1.0"
}

case object MPL_1_1 extends Spdx {
  val spdxId = "MPL-1.1"
  val name = "Mozilla Public License 1.1"
}

case object MPL2 extends Spdx {
  val spdxId = "MPL-2.0"
  val name = "Mozilla Public License 2.0"
}

case object PublicDomain extends License {
  val name = "Public Domain"
  val url = None
}

case object Scala extends License {
  val name = "Scala License"
  val url = Some(new URL("http://www.scala-lang.org/license.html"))
}

case object TypesafeSubscriptionAgreement extends License {
  val name = "Typesafe Subscription Agreement"
  val url = Some(new URL("http://downloads.typesafe.com/website/legal/TypesafeSubscriptionAgreement.pdf"))
}

case object Unlicense extends Spdx {
  val spdxId = "Unlicense"
  val name = "The Unlicense"
}

case object W3C extends Spdx {
  val spdxId = "W3C"
  val name = "W3C Software Notice and License"
}

case object WTFPL extends Spdx {
  val spdxId = "WTFPL"
  val name = "Do What The F*ck You Want To Public License"
}