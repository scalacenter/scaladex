ALTER TABLE project_settings ADD COLUMN category VARCHAR(63);

-- We could drop primary_topic column
-- ALTER TABLE project_settings DROP COLUMN primary_topic;

-- Asynchronous, Concurrent and Distributed Programming

UPDATE project_settings SET category = 'asynchronous-and-reactive-programming'
  WHERE (organization, repository) IN (
    VALUES ('typelevel', 'cats-effect'),
    ('typelevel', 'fs2'),
    ('travisbrown', 'iteratee'),
    ('scalaz', 'scalaz-stream'),
    ('monix', 'monix'),
    ('storm-enroute', 'reactors'),
    ('reactor', 'reactor-scala-extensions'),
    ('rescala-lang', 'rescala'),
    ('reactivex', 'rxscala'),
    ('lihaoyi', 'scala.rx'),
    ('zio', 'zio'),
    ('primetalk', 'synapsegrid'),
    -- ('vert-x3', 'vertx-lang-scala'), not found
    ('wireapp', 'wire-signals'),
    ('verizon', 'delorean'),
    ('traneio', 'arrows'),
    ('reactors-io', 'reactors'),
    ('raquo', 'airstream'),
    ('scala', 'scala-async')
  );

UPDATE project_settings SET category = 'distributed-computing'
  WHERE (organization, repository) IN (
    VALUES ('twitter', 'scalding'),
    ('spotify', 'scio'),
    ('apache', 'spark'),
    ('indix', 'sparkplug'),
    ('twitter', 'summingbird'),
    ('twitter', 'algebird')
    -- ('intel-analytics', 'bigdl'),
    -- ('openmole', 'openmole'),
  );

UPDATE project_settings SET category = 'distributed-messaging-systems-and-microservices'
  WHERE (organization, repository) IN (
    VALUES ('akka', 'akka'), -- reactive programming
    ('annetteplatform', 'annette'),
    ('parapet-io', 'parapet'),
    ('apache', 'flink'),
    ('openmole', 'gridscale'),
    ('apache', 'kafka'),
    ('softwaremill', 'elasticmq'),
    ('softwaremill', 'kmq'),
    ('spingo', 'op-rabbit'),
    ('profunktor', 'fs2-rabbit'),
    ('scalaconsultants', 'reactive-rabbit'),
    ('pjfanning', 'op-rabbit'),
    ('clevercloud', 'pulsar4s'),
    ('lagom', 'lagom'),
    ('line', 'armeria'), -- rpc
    ('twitter', 'finagle'), -- rpc
    ('trex-paxos', 'trex'),
    ('akka-js', 'akka.js'),
    ('twitter', 'finatra'),
    ('tumblr', 'colossus')
    -- ('stephenmcd', 'curiodb'),
    -- ('biddata', 'bidmach'),
    -- ('stratio', 'sparta'),
);

UPDATE project_settings SET category = 'schedulers'
  WHERE (organization, repository) IN (
    VALUES ('enragedginger', 'akka-quartz-scheduler'),
    ('criteo', 'cuttle'),
    ('pagerduty', 'scheduler'),
    ('fthomas', 'fs2-cron'),
    ('maxcellent', 'lamma')
  );

-- Audio and Images

UPDATE project_settings SET category = 'audio-and-music'
  WHERE (organization, repository) IN (
    VALUES ('mziccard', 'scala-audio-file'),
    ('sciss', 'audiofile'),
    ('underscoreio', 'compose'),
    ('sciss', 'strugatzki'),
    ('sciss', 'fscape'),
    ('auroboros', 'scalaudio'),
    ('sciss', 'fscape-next'),
    ('malliina', 'util-audio'),
    ('sciss', 'scalacollider'),
    ('orrsella', 'sbt-sound'),
    ('tototoshi', 'sbt-musical'),
    ('sciss', 'soundprocesses'),
    ('sciss', 'patterns'),
    ('ing-bank', 'baker')
    --  ('mgdigital', 'chromaprint.scala')
  );

UPDATE project_settings SET category = 'video-and-image-processing'
  WHERE (organization, repository) IN (
    VALUES ('bytedeco', 'sbt-javacv'),
    ('sami-badawi', 'shapelogic-scala'),
    ('unibas-gravis', 'scalismo'),
    ('poslegm', 'scala-phash'),
    ('sksamuel', 'scrimage'),
    ('outr', 'media4s') -- audio
  );

--- Big Data and Distributed Systems

UPDATE project_settings SET category = 'data-sources-and-connectors'
  WHERE (organization, repository) IN (
    VALUES ('indix', 'schemer'),
    ('delta-io', 'delta'),
    ('googleclouddataproc', 'spark-bigquery-connector'),
    ('scullxbones', 'akka-persistence-mongo'),
    ('monix', 'monix-connect'),
    ('delta-io', 'connectors'),
    ('datastax', 'spark-cassandra-connector'),
    ('akka', 'alpakka'),
    ('akka', 'alpakka-kafka'),
    ('mongodb', 'mongo-spark'),
    ('memsql', 'singlestore-spark-connector'),
    ('redislabs', 'spark-redis'),
    ('couchbase', 'couchbase-spark-connector'),
    ('microsoft', 'sql-spark-connector'),
    ('springml', 'spark-sftp'),
    ('tuplejump', 'kafka-connect-cassandra'),
    ('databricks', 'spark-csv'),
    ('databricks', 'spark-redshift'),
    ('databricks', 'spark-avro'),
    ('databricks', 'spark-xml'),
    ('zuinnote', 'hadoopoffice')
  );

UPDATE project_settings SET category = 'data-vizualization'
  WHERE (organization, repository) IN (
    VALUES ('vegas-viz', 'vegas'),
    ('cibotech', 'evilplot'),
    ('bokeh', 'bokeh-scala'),
    ('pityka', 'nspl'),
    ('facultyai', 'scala-plotly-client'),
    ('alexarchambault', 'plotly-scala'),
    ('stanch', 'reftree'),
    ('axlelang', 'axle')
  );

--- Configuration, Logging, Testing and Monitoring

UPDATE project_settings SET category = 'command-line-parsing'
  WHERE (organization, repository) IN (
    VALUES ('scopt', 'scopt'),
    ('bkirwi', 'decline'),
    ('deanwampler', 'command-line-arguments'),
    ('alexarchambault', 'case-app'),
    ('backuity', 'clist'),
    ('bmc', 'argot'),
    ('com-lihaoyi', 'mainargs'),
    ('zio', 'zio-cli'),
    ('scallop', 'scallop'),
    ('quantifind', 'sumac')
  );

UPDATE project_settings SET category = 'configuration-and-environment'
  WHERE (organization, repository) IN (
    VALUES ('lightbend', 'config'),
    ('pureconfig', 'pureconfig'),
    ('vlovgr', 'ciris'),
    ('verizon', 'knobs'),
    ('kxbmap', 'configs'),
    ('guardian', 'simple-configuration'),
    ('47degrees', 'case-classy'),
    ('zalando', 'grafter'), -- dependency injection
    ('carueda', 'tscfg'),
    ('carlpulley', 'validated-config'),
    ('scalameta', 'metaconfig'),
    ('lambdista', 'config'),
    ('kovszilard', 'easy-config'),
    ('travisbrown', 'dhallj'),
    ('carlpulley', 'validated-config')
  );

UPDATE project_settings SET category = 'logging'
  WHERE (organization, repository) IN (
    VALUES ('lightbend', 'scala-logging'),
    ('zio', 'zio-logging'),
    ('nestorpersist', 'logging'),
    ('outr', 'scribe'),
    ('lego', 'woof'),
    ('tinylog-org', 'tinylog'),
    ('typelevel', 'log4cats'),
    ('verizon', 'journal'),
    ('lancewalton', 'treelog'),
    ('apache', 'logging-log4j-scala'),
    ('jokade', 'slogging'),
    ('valskalla', 'odin'),
    ('tersesystems', 'blindsight'),
    ('log4s', 'log4s')
  );

UPDATE project_settings SET category = 'performance-and-monitoring'
  WHERE (organization, repository) IN (
    VALUES ('erikvanoosten', 'metrics-scala'),
    ('kenshoo', 'metrics-play'),
    ('banzaicloud', 'spark-metrics'),
    ('kamon-io', 'kamon'),
    ('zio', 'zio-metrics'),
    ('lucacanali', 'sparkmeasure'),
    ('rustedbones', 'akka-http-metrics'),
    ('levkhomich', 'akka-tracing'),
    ('xitrum-framework', 'glokka'),
    ('fiadliel', 'prometheus_client_scala'),
    ('scalacenter', 'scalac-profiling'),
    ('sbt', 'sbt-jmh'),
    ('newrelic', 'newrelic-java-agent'),
    ('japgolly', 'scalajs-benchmark')
  );

UPDATE project_settings SET category = 'testing'
  WHERE (organization, repository) IN (
    VALUES ('agourlay', 'cornichon'),
    ('gatling', 'gatling'),
    ('monix', 'minitest'),
    ('mockito', 'mockito-scala'),
    ('scalameta', 'munit'),
    ('typelevel', 'scalacheck'),
    ('scalameter', 'scalameter'),
    ('paulbutcher', 'scalamock'),
    ('scalaprops', 'scalaprops'),
    ('scalatest', 'scalatest'),
    ('etorreborre', 'specs2'),
    ('disneystreaming', 'weaver-test'),
    ('com-lihaoyi', 'utest'),
    ('holdenk', 'spark-testing-base'),
    ('typelevel', 'cats-effect-testing'),
    ('lensesio', 'kafka-testing'),
    ('mrpowers', 'spark-fast-tests'),
    ('typelevel', 'discipline'),
    ('stryker-mutator', 'stryker4s'),
    ('japgolly', 'test-state'),
    ('scalaz', 'testz'),
    ('propensive', 'probably'),
    ('japgolly', 'nyaya'),
    ('tkawachi', 'sbt-doctest')
  );

-- Databases, Indexing And Searching

UPDATE project_settings SET category = 'databases'
  WHERE (organization, repository) IN (
    VALUES ('innfactory', 'akka-persistence-gcp-datastore'),
    ('playframework', 'anorm'),
    ('mongodb', 'casbah'),
    ('crobox', 'clickhouse-scala-client'),
    ('couchbase', 'couchbase-jvm-clients'),
    ('beloglazov', 'couchdb-scala'),
    ('tpolecat', 'doobie'),
    ('mingchuno', 'etcd4s'),
    ('finagle', 'finagle-postgres'),
    ('laserdisc-io', 'laserdisc'),
    ('laserdisc-io', 'mysql-binlog-stream'),
    ('longevityframework', 'longevity'),
    ('kostaskougios', 'mapperdao'),
    ('outworkers', 'morpheus'),
    ('irevive', 'neotypes'),
    ('outworkers', 'phantom'),
    ('zio', 'zio-quill'),
    ('reactivecouchbase', 'reactivecouchbase-rs-core'),
    ('reactivemongo', 'reactivemongo'),
    ('etaty', 'rediscala'),
    ('lucidsoftware', 'relate'),
    ('salat', 'salat'),
    ('sangria-graphql', 'sangria'),
    ('aselab', 'scala-activerecord'),
    ('lastland', 'scala-forklift'),
    ('debasishg', 'scala-redis'),
    ('wangzaixiang', 'scala-sql'),
    ('outr', 'scalarelational'),
    ('scalikejdbc', 'scalikejdbc'),
    ('guardian', 'scanamo'),
    ('livestream', 'scredis'),
    ('ing-bank', 'scruid'),
    ('monix', 'shade'),
    ('slick', 'slick'),
    ('tminglei', 'slick-pg'),
    ('squeryl', 'squeryl'),
    ('scalamolecule', 'molecule'),
    -- ('zio', 'zio-redis'),
    ('tpolecat', 'skunk'),
    ('netflix', 'atlas'),
    ('flyway', 'flyway'),
    ('flyway', 'flyway-sbt'),
    ('neo4j', 'neo4j')
  );

UPDATE project_settings SET category = 'indexing-and-searching'
  WHERE (organization, repository) IN (
    VALUES ('sksamuel', 'elastic4s'),
    ('outr', 'lucene4s'),
    ('elastic', 'elasticsearch-hadoop'),
    ('bizreach', 'elastic-scala-httpclient'),
    ('alexklibisz', 'elastiknn'),
    ('phymbert', 'spark-search'),
    ('microsoft', 'hyperspace'),
    ('inoio', 'solrs')
  );

--- Time, Position and Units of Measurement

UPDATE project_settings SET category = 'dates-and-time'
  WHERE (organization, repository) IN (
    VALUES ('nscala-time', 'nscala-time'),
    ('typelevel', 'cats-time'),
    ('cquiroz', 'scala-java-time'),
    ('scala-js', 'scala-js-java-time'),
    ('soc', 'scala-java-time'),
    ('alonsodomin', 'cron4s'),
    ('philcali', 'cronish'),
    ('chronoscala', 'chronoscala'),
    ('philippus', 'emoji-clock')
  );

UPDATE project_settings SET category = 'geometry-and-geopositionning'
  WHERE (organization, repository) IN (
    VALUES ('locationtech', 'geotrellis'),
    ('locationtech-labs', 'geopyspark'),
    ('simplexspatial', 'osm4scala'),
    ('plokhotnyuk', 'rtree2d'),
    ('locationtech', 'sfcurve'),
    ('azavea', 'stac4s'),
    ('vitorsvieira', 'scala-iso'),
    ('syoummer', 'spatialspark'),
    ('geolatte', 'geolatte-geom'),
    ('bayer-group', 'mwundo'),
    ('astrolabsoftware', 'spark3d'),
    ('raster-foundry', 'raster-foundry')
    -- ('azavea', 'franklin')
  );

UPDATE project_settings SET category = 'units-of-measurement'
  WHERE (organization, repository) IN (
    VALUES ('karols', 'units'),
    ('typelevel', 'squants'),
    ('erikerlandson', 'coulomb'),
    ('nestorpersist', 'units'),
    ('to-ithaca', 'libra')
  );

--- Deployment, Virtualization and Cloud

UPDATE project_settings SET category = 'deployment-and-cloud'
  WHERE (organization, repository) IN (
    VALUES ('orkestra-tech', 'orkestra'),
    ('getnelson', 'nelson'),
    ('aws4s', 'aws4s'),
    ('zio', 'zio-aws'),
    ('laserdisc-io', 'fs2-aws'),
    ('dwhjames', 'aws-wrap'),
    ('seratch', 'awscala'),
    ('bizreach', 'aws-s3-scala'),
    ('zio', 'zio-sqs'),
    ('chatwork', 'sbt-aws'),
    ('lightbend', 'sbt-google-cloud-storage'),
    ('heroku', 'sbt-heroku'),
    ('vaslabs', 'sbt-kubeyml'),
    ('doriordan', 'skuber'),
    ('hagay3', 'skuber'),
    ('joan38', 'kubernetes-client'),
    ('hseeberger', 'constructr'),
    ('tapad', 'sbt-marathon'),
    ('archived-codacy', 'scala-consul'),
    ('verizon', 'helm'),
    ('tecsisa', 'constructr-consul'),
    ('dlouwers', 'reactive-consul'),
    ('akka', 'akka-management'),
    ('lightbend', 'service-locator-dns'),
    ('bayer-group', 'cloudformation-template-generator'),
    ('paypal', 'squbs'),
    ('coralogix', 'zio-k8s'),
    ('toknapp', 'google4s')
  );

UPDATE project_settings SET category = 'packaging-and-publishing'
  WHERE (organization, repository) IN (
    VALUES ('sbt', 'sbt-native-packager'), -- containerization
    ('xerial', 'sbt-pack'),
    ('sbt', 'sbt-onejar'),
    ('sbt', 'sbt-bintray'),
    ('djspiewak', 'sbt-github-packages'),
    ('er1c', 'sbt-github'),
    ('jodersky', 'sbt-gpg'),
    ('sbt', 'sbt-pgp'),
    ('jvican', 'sbt-release-early'),
    ('typelevel', 'sbt-typelevel'),
    ('laughedelic', 'sbt-publish-more'),
    ('sbt', 'sbt-release'),
    ('sbt', 'sbt-ci-release'),
    ('xerial', 'sbt-sonatype'),
    ('hammerlab', 'sbt-parent'),
    ('scalameta', 'sbt-native-image'),
    ('sbt', 'sbt-assembly')
  );

UPDATE project_settings SET category = 'library-dependency-management'
  WHERE (organization, repository) IN (
    VALUES ('scalacenter', 'scaladex'),
    ('coursier', 'coursier'),
    ('scala-steward-org', 'scala-steward'),
    ('sbt', 'sbt-dependency-graph'),
    ('sbt', 'librarymanagement'),
    ('albuch', 'sbt-dependency-check'),
    ('aiyanbo', 'sbt-dependency-updates'),
    ('rtimush', 'sbt-updates')
  );

UPDATE project_settings SET category = 'serverless'
  WHERE (organization, repository) IN (
    VALUES ('itv', 'chuckwagon'),
    ('mkotsur', 'aws-lambda-scala'),
    ('carpe', 'scalambda'),
    ('yoshiyoshifujii', 'sbt-aws-serverless'),
    ('cloudstateio', 'cloudstate')
  );


UPDATE project_settings SET category = 'version-management'
  WHERE (organization, repository) IN (
    VALUES ('dwijnand', 'sbt-dynver'),
    ('gitbucket', 'gitbucket'),
    ('sbt', 'sbt-git'),
    ('sbt', 'sbt-groll'),
    ('rallyhealth', 'sbt-git-versioning'),
    ('randomcoder', 'sbt-git-hooks')
  );

UPDATE project_settings SET category = 'virtualization-and-containerization'
  WHERE (organization, repository) IN (
    VALUES ('whisklabs', 'docker-it-scala'),
    ('vonnagy', 'service-container'),
    ('aloiscochard', 'sindi'),
    ('j5ik2o', 'docker-controller-scala'),
    ('marcuslonnberg', 'sbt-docker'),
    ('tapad', 'sbt-docker-compose'),
    ('ehsanyou', 'sbt-docker-compose'),
    ('nokia', 'mesos-scala-api'),
    ('testcontainers', 'testcontainers-scala') -- testing
  );

--- Development Tooling

UPDATE project_settings SET category = 'build-tools'
  WHERE (organization, repository) IN (
    VALUES ('scalacenter', 'bloop'),
    ('sbt', 'sbt'),
    ('pantsbuild', 'pants'),
    ('com-lihaoyi', 'mill'),
    ('build-server-protocol', 'build-server-protocol')
  );

UPDATE project_settings SET category = 'code-analysis'
  WHERE (organization, repository) IN (
    VALUES ('pmd', 'pmd'),
    ('joernio', 'joern'),
    ('codacy', 'codacy-analysis-cli'),
    ('sonar-scala', 'sonar-scala'),
    ('lightbend', 'mima'),
    ('lolgab', 'mill-mima'),
    ('scalameta', 'scalameta'),
    ('virtuslab', 'graphbuddy'),
    ('scoverage', 'sbt-scoverage'),
    ('scoverage', 'scalac-scoverage-plugin'),
    ('scoverage', 'sbt-coveralls'),
    ('codacy', 'codacy-scalameta'),
    ('bottech', 'scala2plantuml')
    -- ('johnreedlol', 'pos')
  );

UPDATE project_settings SET category = 'linting-and-refactoring'
  WHERE (organization, repository) IN (
    VALUES ('scalastyle', 'scalastyle'),
    ('scapegoat-scala', 'scapegoat'),
    ('hairyfotr', 'linter'),
    ('scalacenter', 'scalafix'),
    ('wartremover', 'wartremover'),
    ('tanishiking', 'scalafix-check-scaladoc'),
    ('sbt', 'sbt-jshint'),
    ('scala-ide', 'scala-refactoring')
  );

UPDATE project_settings SET category = 'printing-and-debugging'
  WHERE (organization, repository) IN (
    VALUES ('johnreedlol', 'scala-trace-debug'),
    ('adamw', 'scala-macro-debug'),
    ('scalacenter', 'scala-debug-adapter'),
    ('com-lihaoyi', 'sourcecode'),
    ('virtuslab', 'pretty-stacktraces'),
    ('com-lihaoyi', 'pprint')
    -- ('johnreedlol', 'pos'),
  );

UPDATE project_settings SET category = 'code-editors-and-notebooks'
  WHERE (organization, repository) IN (
    VALUES ('sciss', 'dotterweide'),
    ('scalameta', 'metals'),
    ('jetbrains', 'sbt-ide-settings'),
    ('polynote', 'polynote'),
    ('almond-sh', 'almond'),
    ('sbt', 'sbteclipse')
    -- ('scalacenter', 'scastie')
    -- ('scalafiddle', 'scalafiddle-editor')
    -- ('spark-notebook', 'spark-notebook'),
    -- ('apache', 'zeppelin')
  );

UPDATE project_settings SET category = 'code-formatting'
  WHERE (organization, repository) IN (
    VALUES ('sbt', 'sbt-scalariform'),
    ('sbt', 'sbt-java-formatter'),
    ('scalameta', 'scalafmt'),
    ('scalameta', 'sbt-scalafmt'),
    ('lucidsoftware', 'neo-sbt-scalafmt'),
    ('tanishiking', 'scalaunfmt')
  );

UPDATE project_settings SET category = 'scripting-and-repls'
  WHERE (organization, repository) IN (
    VALUES ('com-lihaoyi', 'ammonite'),
    ('razie', 'scripster'),
    ('woshilaiceshide', 'scala-web-repl'),
    ('masseguillaume', 'scalakata2'),
    ('dbdahl', 'rscala')
    -- ('xitrum-framework', 'scalive')
    -- ('marconilanna', 'replesent')
  );

UPDATE project_settings SET category = 'miscellaneous-tools'
  WHERE (organization, repository) IN (
    VALUES ('sbt', 'sbt-header'),
    ('spray', 'sbt-revolver'),
    ('foundweekends', 'giter8')
  );

--- General Computer Science

UPDATE project_settings SET category = 'caching'
  WHERE (organization, repository) IN (
    VALUES ('cb372', 'scalacache'),
    ('blemale', 'scaffeine'),
    ('jcouyang', 'jujiu'),
    ('zio', 'zio-cache')
  );

UPDATE project_settings SET category = 'compilers'
  WHERE (organization, repository) IN (
    VALUES ('twitter', 'rsc'),
    ('scala', 'scala'),
    ('lampepfl', 'dotty'),
    ('softwaremill', 'scala-clippy'),
    ('ghik', 'silencer'),
    ('sbt', 'zinc'),
    ('scala-js', 'scala-js'),
    ('scala-native', 'scala-native'),
    ('davidgregory084', 'sbt-tpolecat'),
    ('com-lihaoyi', 'acyclic'),
    ('scala-ts', 'scala-ts'),
    ('tek', 'splain')
  );

UPDATE project_settings SET category = 'code-generation'
  WHERE (organization, repository) IN (
    VALUES 
    ('olafurpg', 'scala-db-codegen'),
    ('sbt', 'sbt-buildinfo'),
    ('thesamet', 'sbt-protoc'),
    ('fralken', 'sbt-swagger-codegen'),
    ('sbt', 'sbt-boilerplate'),
    ('mediative', 'sangria-codegen')
  );

UPDATE project_settings SET category = 'scala-language-extensions'
  WHERE (organization, repository) IN (
    VALUES ('thoughtworksinc', 'dsl.scala'),
    ('typelevel', 'kind-projector'),
    ('oleg-py', 'better-monadic-for'),
    ('alexarchambault', 'data-class'),
    ('thoughtworksinc', 'each'),
    ('thoughtworksinc', 'enableif.scala'),
    ('lloydmeta', 'enumeratum'),
    ('scalalandio', 'chimney'),
    ('milessabin', 'shapeless'),
    ('typelevel', 'shapeless-3'),
    ('softwaremill', 'magnolia'),
    ('thangiee', 'freasy-monad'),
    ('iscpif', 'freedsl'),
    ('scala-records', 'scala-records'),
    ('typelevel', 'simulacrum'),
    ('miniboxing', 'miniboxing-plugin')
  );

UPDATE project_settings SET category = 'miscellaneous-utils'
  WHERE (organization, repository) IN (
    VALUES 
    ('scala-hamsters', 'hamsters'),
    ('twitter', 'util'),
    ('outworkers', 'util'),
    ('bmc', 'grizzled-scala'),
    ('jsuereth', 'scala-arm'),
    ('iravid', 'managedt'),
    ('com-lihaoyi', 'geny'),
    ('7mind', 'izumi'),
    ('wvlet', 'airframe'),
    ('xitrum-framework', 'sclasner'),
    ('dvgica', 'managerial')
  );

UPDATE project_settings SET category = 'functionnal-programming-and-category-theory'
  WHERE (organization, repository) IN (
    VALUES ('zio', 'zio-prelude'),
    ('jcouyang', 'meow'),
    ('propensive', 'mercator'),
    ('manatki', 'volga'),
    ('typelevel', 'cats'),
    ('scalaz', 'scalaz'),
    ('precog', 'matryoshka'),
    ('higherkindness', 'droste'),
    ('davidgregory084', 'schemes'),
    ('circe', 'circe-droste'),
    ('djspiewak', 'shims'),
    ('atnos-org', 'eff'),
    ('frees-io', 'freestyle'),
    ('julien-truffaut', 'monocle'),
    ('softwaremill', 'quicklens'),
    ('tofu-tf', 'tofu')
  );

UPDATE project_settings SET category = 'logic-programming-and-type-constraints'
  WHERE (organization, repository) IN (
    VALUES ('tomasmikula', 'nutcracker'),
    ('epfl-lara', 'stainless'),
    ('uuverifiers', 'ostrich'),
    ('sciss', 'poirot'),
    ('iltotore', 'iron'),
    ('fthomas', 'refined'),
    ('epfl-lara', 'inox'),
    ('vivri', 'adjective')
  );

UPDATE project_settings SET category = 'algorithms-and-data-structures'
  WHERE (organization, repository) IN (
    VALUES ('typelevel', 'cats-collections'),
    ('non', 'debox'),
    ('denisrosset', 'metal'),
    ('findify', 'scala-packed'),
    ('scala', 'scala-parallel-collections'),
    ('rklaehn', 'abc'),
    ('scala', 'scala-collection-contrib'),
    ('sciss', 'kollflitz'),
    ('scala', 'scala-collection-compat'), -- also compatibility
    ('twitter', 'cassovary'),
    ('xerial', 'larray'),
    ('scala-graph', 'scala-graph'),
    ('stripe', 'bonsai')
  );

UPDATE project_settings SET category = 'dependency-injection'
  WHERE (organization, repository) IN (
    VALUES ('softwaremill', 'macwire'),
    ('scaldi', 'scaldi'),
    ('scalalandio', 'pulp'),
    ('giiita', 'refuel'),
    ('codingwell', 'scala-guice'),
    ('dickwall', 'subcut'),
    ('yakivy', 'jam')
  );

UPDATE project_settings SET category = 'parsing'
  WHERE (organization, repository) IN (
    VALUES ('tpolecat', 'atto'),
    ('scala', 'scala-parser-combinators'),
    ('djspiewak', 'parseback'),
    ('sirthias', 'parboiled'),
    ('typelevel', 'cats-parse'),
    ('com-lihaoyi', 'fastparse')
    -- ('epfl-lara', 'scallion')
  );

UPDATE project_settings SET category = 'programming-language-interfaces'
  WHERE (organization, repository) IN (
    VALUES ('shadaj', 'scalapy'),
    ('scala-native', 'scala-native-bindgen'),
    ('scalablytyped', 'converter')
  );

--- Mathematics, Data Science, Finance and Bioinformatics

UPDATE project_settings SET category = 'bioinformatics'
  WHERE (organization, repository) IN (
    VALUES ('bigdatagenomics', 'adam'),
    ('fulcrumgenomics', 'fgbio'),
    ('bigdatagenomics', 'cannoli'),
    ('aehrc', 'variantspark'),
    ('clulab', 'reach'),
    ('darrenjw', 'scala-smfsb'),
    ('projectglow', 'glow')
  );

UPDATE project_settings SET category = 'cryptography-and-hashing'
  WHERE (organization, repository) IN (
    VALUES ('jmcardon', 'tsec'),
    ('wavesplatform', 'waves'),
    ('ironcorelabs', 'recrypt'),
    ('typelevel', 'bobcats'),
    ('input-output-hk', 'scrypto'),
    ('desmondyeung', 'scala-hashing'),
    ('nycto', 'hasher'),
    ('topl', 'bifrost'),
    ('hyperledger-labs', 'scorex'),
    ('input-output-hk', 'scorex'),
    ('groestlcoin', 'eclair')
  );

UPDATE project_settings SET category = 'economy-finance-and-cryptocurrencies'
  WHERE (organization, repository) IN (
    VALUES ('scala-rules', 'finance-dsl'),
    ('openquant', 'yahoofinancescala'),
    ('quantarray', 'skylark'),
    ('snowplow', 'scala-forex'),
    ('lambdista', 'money'),
    ('taintech', 'bittrex-scala-client'),
    ('bitcoin-s', 'bitcoin-s'),
    ('synesso', 'scala-stellar-sdk'),
    ('scalabm', 'auctions')
  );

UPDATE project_settings SET category = 'probability-statistics-and-machine-learning'
  WHERE (organization, repository) IN (
    VALUES ('haifengl', 'smile'),
    ('microsoft', 'synapseml'),
    ('apache', 'predictionio'),
    ('dmlc', 'xgboost'),
    ('h2oai', 'h2o-3'),
    ('eaplatanios', 'tensorflow_scala'),
    ('scalanlp', 'nak'),
    ('etsy', 'conjecture'),
    ('salesforce', 'transmogrifai'),
    ('alibaba', 'alink'),
    ('byzer-org', 'byzer-lang'),
    ('tailhq', 'dynaml'),
    ('catboost', 'catboost'),
    ('picnicml', 'doddle-model'),
    ('spotify', 'featran'),
    ('h2oai', 'sparkling-water'),
    ('amplab', 'keystone'),
    ('apache', 'flink-ml'),
    ('thoughtworksinc', 'deeplearning.scala'),
    ('eclipse', 'deeplearning4j'),
    ('cloudml', 'zen'),
    ('ciren', 'cilib'),
    ('botkop', 'scorch'),
    ('deeplearning4j', 'scalnet'),
    ('deeplearning4j', 'deeplearning4j'),
    ('deeplearning4j', 'arbiter'),
    ('clustering4ever', 'clustering4ever'),
    ('openmole', 'mgo'), -- also genetic
    ('emergentorder', 'onnx-scala'),
    ('mrdimosthenis', 'synapses'),
    ('neysofu', 'tyche'),
    ('stripe-archive', 'brushfire')
    -- ('p2t2', 'figaro'),
    -- ('anskarl', 'lomrf'),
    -- ('deeplearning4j', 'nd4s')
  );

UPDATE project_settings SET category = 'natural-language-processing'
  WHERE (organization, repository) IN (
    VALUES ('johnsnowlabs', 'spark-nlp'),
    ('scalanlp', 'chalk'),
    ('clulab', 'processors'),
    ('knowitall', 'nlptools'),
    ('uosdmlab', 'spark-nkp')
  );

UPDATE project_settings SET category = 'numerical-and-symbolic-computing'
  WHERE (organization, repository) IN (
    VALUES ('scalanlp', 'breeze'),
    -- ('sciscala', 'ndscala'),
    ('botkop', 'numsca'),
    ('vagmcs', 'optimus'),
    ('poslavskysv', 'rings'),
    ('typelevel', 'spire'),
    ('cascala', 'galileo'),
    ('facsimile', 'facsimile'),
    ('nasarace', 'race')
  );

--- Mobile, Desktop and Game Development

UPDATE project_settings SET category = 'mobile'
  WHERE (organization, repository) IN (
    VALUES ('scala-android', 'sbt-android'),
    ('pocorall', 'scaloid'),
    ('shadowsocks', 'shadowsocks-android'),
    ('stripe', 'stripe-android'),
    ('didi', 'booster'),
    ('aws-amplify', 'aws-sdk-android'),
    ('47degrees', 'macroid'),
    ('scala-android', 'sbt-android-protify')
  );

UPDATE project_settings SET category = 'graphical-interfaces-and-game-development'
  WHERE (organization, repository) IN (
    VALUES ('regb', 'scalanative-graphics-bindings'),
    ('simerplaha', 'slack3d'),
    ('purplekingdomgames', 'indigo'),
    ('cswinter', 'codecraftgame'),
    ('creativescala', 'doodle'),
    ('scalafx', 'scalafx')
    -- ('marianogappa', 'ostinato'),
  );

--- Operating System, Hardware and Robotics

UPDATE project_settings SET category = 'hardware-and-emulators'
  WHERE (organization, repository) IN (
    VALUES ('edadma', 'mos6502'),
    ('edadma', 'riscv'),
    ('chipsalliance', 'rocket-chip'),
    ('chipsalliance', 'chisel3'),
    ('freechipsproject', 'chisel-testers'),
    ('chipsalliance', 'treadle'),
    ('ucb-bar', 'chiseltest'),
    ('sifive', 'chisel-circt'),
    ('freechipsproject', 'diagrammer'),
    ('ucb-bar', 'dsptools'),
    ('ucb-bar', 'berkeley-hardfloat'),
    ('freechipsproject', 'firrtl-interpreter'),
    ('chipsalliance', 'firrtl'),
    ('powerapi-ng', 'powerapi-scala'),
    ('spinalhdl', 'spinalhdl')
  );

UPDATE project_settings SET category = 'file-systems-and-processes'
  WHERE (organization, repository) IN (
    VALUES ('pathikrit', 'better-files'),
    ('gmethvin', 'directory-watcher'),
    ('lloydmeta', 'schwatcher'),
    ('com-lihaoyi', 'os-lib'),
    ('zio', 'zio-process'),
    ('sbt', 'sbt-sh'),
    ('hammerlab', 'io-utils')
  );

UPDATE project_settings SET category = 'network'
  WHERE (organization, repository) IN (
    VALUES ('comcast', 'ip4s'),
    ('richrelevance', 'scalaz-netty'),
    ('rsocket', 'rsocket-transport-akka'),
    ('softprops', 'unisockets'),
    ('reactor', 'reactor'),
    ('risksense', 'ipaddr'),
    ('lorancechen', 'rxsocket'),
    ('sirthias', 'scala-ssh')
  );

-- Text, Formats and Compression

UPDATE project_settings SET category = 'archives-and-compression'
  WHERE (organization, repository) IN (
    VALUES ('avast', 'bytecompressor'),
    ('gekomad', 'scala-compress')
    -- ('gonearewe', 'sevenz4s')
  );

UPDATE project_settings SET category = 'csv'
  WHERE (organization, repository) IN (
    VALUES ('frugalmechanic', 'fm-flatfile'),
    ('nrinaudo', 'kantan.csv'),
    ('tototoshi', 'scala-csv'),
    ('fingo', 'spata'),
    ('melrief', 'purecsv'),
    ('scalikejdbc', 'csvquery'),
    ('davenverse', 'cormorant'),
    ('zamblauskas', 'scala-csv-parser')
  );

UPDATE project_settings SET category = 'json'
  WHERE (organization, repository) IN (
    VALUES ('argonaut-io', 'argonaut'),
    ('sirthias', 'borer'),
    ('circe', 'circe'),
    ('gnieh', 'diffson'),
    ('fasterxml', 'jackson-module-scala'),
    ('typelevel', 'jawn'),
    ('json4s', 'json4s'),
    ('plokhotnyuk', 'jsoniter-scala'),
    ('nestorpersist', 'json'),
    ('kag0', 'ninny-json'),
    ('playframework', 'play-json'),
    ('fomkin', 'pushka'),
    ('battermann', 'sbt-json'),
    ('qvantel', 'scala-jsonapi'),
    ('gzoller', 'scalajack'),
    ('spray', 'spray-json'),
    ('ngs-doo', 'dsl-json'),
    ('zio', 'zio-json'),
    ('jrudolph', 'json-lenses'),
    ('tethys-json', 'tethys'),
    ('hseeberger', 'akka-http-json'),
    ('bizzabo', 'play-json-extensions'),
    ('kifi', 'json-annotation'),
    ('jvican', 'dijon'),
    ('maffoo', 'jsonquote')
  );

UPDATE project_settings SET category = 'markdown'
  WHERE (organization, repository) IN (
    VALUES ('planet42', 'laika'),
    ('foundweekends', 'pamflet'),
    ('esamson', 'remder'),
    ('scalatra', 'scalamd'),
    ('noelwelsh', 'mads')
  );

UPDATE project_settings SET category = 'pdf'
  WHERE (organization, repository) IN (
    VALUES ('allenai', 'pdffigures2'),
    ('allenai', 'science-parse'),
    ('hhandoko', 'play2-scala-pdf'),
    ('cloudify', 'spdf'),
    ('growinscala', 'flipper'),
    ('overview', 'pdfocr'),
    ('springernature', 'fs2-pdf'), 
    ('outr', 'pdf4s')
  );

UPDATE project_settings SET category = 'serialization'
  WHERE (organization, repository) IN (
    VALUES ('sksamuel', 'avro4s'),
    -- ('malcolmgreaves', 'avro-codegen'),
    ('twitter', 'chill'),
    ('msgpack', 'msgpack-scala'),
    ('scalapb', 'scalapb'),
    ('scodec', 'scodec'),
    ('twitter', 'scrooge'),
    ('com-lihaoyi', 'upickle'),
    ('sbt', 'serialization'),
    ('suzaku-io', 'boopickle'), -- network
    ('vigoo', 'desert'),
    ('jodersky', 'akka-serial'),
    ('json4s', 'muster'),
    ('satabin', 'fs2-data'),
    ('scala', 'pickling')
  );

UPDATE project_settings SET category = 'templating'
  WHERE (organization, repository) IN (
    VALUES ('playframework', 'twirl'),
    ('scala-thera', 'thera'),
    ('mwunsch', 'handlebars.scala'),
    ('zalando', 'beard'),
    ('vmunier', 'scalajs-scripts'),
    ('sake92', 'hepek'), --html
    ('scalate', 'scalate')
  );

UPDATE project_settings SET category = 'text-manipulation'
  WHERE (organization, repository) IN (
    VALUES ('peoplepattern', 'lib-text'),
    ('bizzabo', 'diff'),
    ('tulz-app', 'stringdiff'),
    ('propensive', 'contextual'),
    ('plokhotnyuk', 'fast-string-interpolator'),
    ('atry', 'fastring'),
    ('com-lihaoyi', 'fansi'),
    ('rockymadden', 'stringmetric'),
    ('vickumar1981', 'stringdistance'),
    ('dwijnand', 'tabular'),
    ('colofabrix', 'figlet4s'),
    ('sirthias', 'spliff'),
    ('marianobarrios', 'dregex'),
    ('todokr', 'emojipolation')
  );

UPDATE project_settings SET category = 'other-document-formats'
  WHERE (organization, repository) IN (
    VALUES ('tomtung', 'latex2unicode'),
    ('jphmrst', 'scala-latex'),
    ('jphmrst', 'scala-outlines'),
    ('lihaoyi', 'scalatex')
  );

UPDATE project_settings SET category = 'yaml'
  WHERE (organization, repository) IN (
    VALUES ('circe', 'circe-yaml'),
    ('virtuslab', 'scala-yaml'),
    ('jodersky', 'yamlesque'),
    ('jcazevedo', 'moultingyaml')
  );

UPDATE project_settings SET category = 'xml-html-and-dom'
  WHERE (organization, repository) IN (
    VALUES ('scala', 'scala-xml'),
    ('eed3si9n', 'scalaxb'),
    ('com-lihaoyi', 'scalatags'),
    ('dylemma', 'xml-spac'),
    ('note', 'xml-lens'),
    ('scalawilliam', 'xs4s'),
    ('chris-twiner', 'scalesxml'),
    ('sparsetech', 'pine'),
    ('lucidsoftware', 'xtract'),
    ('geirolz', 'advxml'),
    ('dvreeze', 'yaidom'),
    ('ohze', 'scala-soap'),
    ('andyglow', 'scala-xml-diff'),
    ('ruippeixotog', 'scala-scraper'),
    ('olivierblanvillain', 'monadic-html'),
    ('fomkin', 'levsha'),
    ('raquo', 'scala-dom-types'),
    ('intenthq', 'gander'),
    ('scala-js', 'scala-js-dom'),
    ('raquo', 'scala-dom-builder')
  );

-- Web Development

UPDATE project_settings SET category = 'asset-management-and-bundlers'
  WHERE (organization, repository) IN (
    VALUES ('mmizutani', 'sbt-play-gulp'),
    ('sbt', 'sbt-gzip'),
    ('karasiq', 'sbt-scalajs-bundler'),
    ('givesocialmovement', 'sbt-webpack'),
    ('sbt', 'sbt-concat'),
    ('sbt', 'sbt-web'),
    ('vmunier', 'sbt-web-scalajs'),
    ('lolhens', 'sbt-css-compress'),
    ('mdedetrich', 'akka-http-webjars'),
    ('arpnetworking', 'sbt-typescript'),
    ('irundaia', 'sbt-sassify'),
    ('sbt', 'sbt-digest'),
    ('bowlingx', 'play-webpack'),
    ('scalacenter', 'scalajs-bundler'),
    ('thoughtworksinc', 'sbt-scala-js-map')
  );

UPDATE project_settings SET category = 'authentication-and-permissions'
  WHERE (organization, repository) IN (
    VALUES ('softwaremill', 'akka-http-session'),
    ('ticofab', 'aws-request-signer'),
    -- ('zalando-stups', 'oauth2-mock-play'),
    ('guardian', 'play-googleauth'),
    ('pac4j', 'play-pac4j'),
    ('t2v', 'play2-auth'),
    ('nulab', 'scala-oauth2-provider'),
    ('jaliss', 'securesocial'),
    ('mohiva', 'play-silhouette'),
    ('joscha', 'play-authenticate'),
    ('profunktor', 'http4s-jwt-auth'),
    ('kovacshuni', 'koauth'),
    ('guardian', 'pan-domain-authentication'),
    ('pac4j', 'vertx-pac4j'),
    ('innfactory', 'akka-jwt'),
    ('guardian', 'play-googleauth')
  ); 

UPDATE project_settings SET category = 'forms-and-validation'
  WHERE (organization, repository) IN (
    VALUES ('gitbucket', 'scalatra-forms'),
    ('voltir', 'form.rx'),
    ('kitlangton', 'formula'),
    ('jto', 'validation'),
    ('wix', 'accord'),
    ('krzemin', 'octopus'),
    ('bean-validation-scala', 'bean-validation-scala'),
    ('davegurnell', 'checklist'),
    ('eclipsesource', 'play-json-schema-validator'),
    ('typelevel', 'literally'),
    ('rewards-network', 'combos'),
    ('tminglei', 'form-binder'),
    ('yakivy', 'dupin')
    -- ('splink', 'veto')
  ); 

UPDATE project_settings SET category = 'emailing'
  WHERE (organization, repository) IN (
    VALUES ('playframework', 'play-mailer'),
    ('hmrc', 'emailaddress'),
    ('eikek', 'emil'),
    ('dmurvihill', 'courier'),
    ('spinoco', 'fs2-mail'),
    ('jurajburian', 'mailer'),
    ('outr', 'mailgun4s')
  );  

UPDATE project_settings SET category = 'http-servers-and-clients'
  WHERE (organization, repository) IN (
    VALUES ('akka', 'akka-http'),
    ('dispatch', 'reboot'),
    ('finagle', 'finch'),
    ('daviddenton', 'fintrospect'),
    ('http4s', 'http4s'),
    ('outr', 'jefe'),
    ('criteo', 'lolhttp'),
    ('com-lihaoyi', 'requests-scala'),
    ('hmil', 'roshttp'),
    ('scalaj', 'scalaj-http'),
    ('softwaremill', 'sttp'),
    ('softwaremill', 'tapir'),
    ('endpoints4s', 'endpoints4s'),
    ('dream11', 'zio-http'),
    ('spinoco', 'fs2-http'),
    ('lomigmegard', 'akka-http-cors'),
    ('com-lihaoyi', 'cask'),
    ('unfiltered', 'unfiltered'),
    ('ollls', 'zio-tls-http'),
    ('http4s', 'blaze'),
    ('pepegar', 'hammock'),
    ('akka', 'akka-grpc'),
    ('lhotari', 'akka-http-health'),
    ('higherkindness', 'mu-scala'),
    ('verizon', 'remotely'),
    ('lihaoyi', 'autowire'),
    ('buildo', 'wiro'),
    ('jducoeur', 'jquery-facade'),
    ('sjrd', 'scala-js-jquery'),
    ('udashframework', 'scala-js-jquery'),
    ('timeoutdigital', 'docless'),
    ('swagger-api', 'swagger-core'),
    ('iheartradio', 'play-swagger'),
    ('swagger-api', 'swagger-play'),
    ('swagger-akka-http', 'swagger-akka-http'),
    ('xiaodongw', 'swagger-finatra'),
    ('http4s', 'rho'),
    ('pathikrit', 'metarest'),
    ('pheymann', 'typedapi'),
    ('playframework', 'play-ws'),
    ('analogweb', 'analogweb-scala'),
    ('lift', 'framework'),
    ('playframework', 'playframework'),
    ('splink', 'pagelets'),
    ('scalatra', 'scalatra'),
    ('skinny-framework', 'skinny-framework'),
    ('xitrum-framework', 'xitrum'),
    ('yakivy', 'poppet'),
    -- ('softwaremill', 'bootzooka'),
    ('earldouglas', 'xsbt-web-plugin')
    -- ('allawala', 'service-chassis')
    -- ('dvarelap', 'peregrine'),
    -- ('mesosphere', 'chaos')
  );

UPDATE project_settings SET category = 'internationalization'
  WHERE (organization, repository) IN (
    VALUES ('xitrum-framework', 'scala-xgettext'),
    ('xitrum-framework', 'scaposer'),
    ('s-mach', 's_mach.i18n'),
    ('osinka', 'scala-i18n'),
    ('marcospereira', 'play-i18n-hocon'),
    ('karelcemus', 'play-i18n'),
    ('ant8e', 'sbt-i18n'),
    ('makkarpov', 'scalingua'),
    ('taig', 'babel'),
    ('fbaierl', 'scalajs-i18n-cx')
  );

UPDATE project_settings SET category = 'static-sites-and-documentation'
  WHERE (organization, repository) IN (
    VALUES ('sbt', 'sbt-site'),
    ('sbt', 'sbt-ghpages'),
    ('szeiger', 'ornate'),
    ('lightbend', 'paradox'),
    ('scalameta', 'mdoc'),
    ('scalameta', 'metabrowse'),
    ('laughedelic', 'literator'),
    ('criteo', 'socco'),
    ('sbt', 'sbt-unidoc'),
    ('scala-search', 'scaps'),
    ('virtuslab', 'inkuire'),
    ('thoughtworksinc', 'sbt-api-mappings'),
    ('valydia', 'sbt-apidoc'),
    ('sake92', 'sbt-hepek'),
    ('47degrees', 'sbt-microsites'),
    ('tpolecat', 'tut')
  );

UPDATE project_settings SET category = 'third-party-apis'
  WHERE (organization, repository) IN (
    VALUES ('bot4s', 'telegram'),
    ('47degrees', 'github4s'),
    ('augustjune', 'canoe'),
    ('guardian', 'content-api-scala-client'),
    ('danielasfregola', 'twitter4s'),
    ('slack-scala-client', 'slack-scala-client'),
    ('eckerdcollege', 'google-api-scala'),
    ('snowplow', 'scala-weather')
  );

UPDATE project_settings SET category = 'urls-and-routing'
  WHERE (organization, repository) IN (
    VALUES ('lemonlabsuck', 'scala-uri'),
    ('sherpal', 'url-dsl'),
    ('olafurpg', 'tiny-router'),
    ('voltir', 'route.rx'),
    ('hmrc', 'url-builder'),
    ('f100ded', 'scala-url-builder'),
    ('raquo', 'waypoint'),
    ('sparsetech', 'trail'),
    ('tulz-app', 'frontroute'),
    ('tulz-app', 'laminar-router'),
    ('sake92', 'scalajs-router'),
    ('sake92', 'stone')
  );

UPDATE project_settings SET category = 'web-frontend'
  WHERE (organization, repository) IN (
    VALUES ('japgolly', 'scalajs-react'),
    ('shadaj', 'slinky'),
    ('chandu0101', 'sri'),
    ('chandu0101', 'scalajs-react-components'),
    ('chandu0101', 'scalajs-react-native'),
    ('aappddeevv', 'scalajs-reaction'),
    ('payalabs', 'scalajs-react-bridge'),
    ('dispalt', 'sbt-reactjs'),
    ('japgolly', 'scalacss'),
    ('kinoplan', 'scalajs-react-material-ui'),
    ('raquo', 'laminar'),
    ('kitlangton', 'animus'),
    ('tulz-app', 'laminext'),
    ('greencatsoft', 'scalajs-angular'),
    ('jokade', 'angulate2'),
    ('jokade', 'scalajs-angulate'),
    ('outwatch', 'outwatch'),
    ('fomkin', 'korolev'),
    ('udashframework', 'udash-core'),
    ('oleg-py', 'shironeko'),
    ('thoughtworksinc', 'binding.scala'),
    ('nafg', 'reactive'),
    ('outr', 'youi')
  );

UPDATE project_settings SET category = 'semantic-web'
  WHERE (organization, repository) IN (
    VALUES ('banana-rdf', 'banana-rdf'),
    ('phenoscape', 'scowl')
  );
