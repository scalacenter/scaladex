-- Remove all artifacts on Scala 3 pre-release versions
DELETE FROM artifacts WHERE platform LIKE '%_3.%';
