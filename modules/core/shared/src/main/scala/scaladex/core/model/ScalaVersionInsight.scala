package scaladex.core.model

enum InsightsGranularity:
  // The true binary-compatible target, e.g. every Scala 3.x minor collapsed into one "3" bucket.
  case BinaryCompat
  // The minor version a release was actually built with, e.g. Scala 3.x split into 3.0, 3.1, 3.2...
  case Minor

// How many projects have, among their latest artifacts, at least one built for this Scala version.
case class ScalaVersionInsight(granularity: InsightsGranularity, language: Language, projectCount: Long)

// How many projects have, among their latest artifacts, at least one built for Scala 3 (migrated = true)
// versus only Scala 2.x ones (migrated = false).
case class Scala3MigrationInsight(migrated: Boolean, projectCount: Long)
