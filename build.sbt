import sbt.artifactFilter

name := "stanford-corenlp-shaded"

version := "0.1"

scalaVersion := "2.11.12"


val appsPath = sys.env.getOrElse("APPS_PATH", "../apps")

lazy val coreNLPmodels = taskKey[Unit](
  s"Put corenlp model into directory $appsPath/corenlp.")
lazy val dependencyLib = taskKey[Unit](
  s"Put dependency libraries in $appsPath/lib_managed.")

lazy val coreNLPcompileShaded = inputKey[Unit]("Create corenlp shaded library " +
  "and deploy to the nexus repository with provide login and password")

coreNLPcompileShaded := {
  import scala.sys.process._

  val commitFor3_9_1 = "0ff7514936db082bbcaab66b2f1940de363dfb64"

  val log = streams.value.log
  val coreNLPdirectory : File  = baseDirectory.value / "CoreNLP"
  val procLog = ProcessLogger(l=> log.info(l),l=> log.info(l))
  if ( coreNLPdirectory.exists()) {
    log.info("Git reset and pull")
    Process("git"::"reset"::"--hard"::Nil,coreNLPdirectory) ! procLog
    Process("git"::"pull"::Nil,coreNLPdirectory) ! procLog
    Process("git"::"checkout"::s"$commitFor3_9_1"::Nil, coreNLPdirectory) ! procLog
  } else {
    log.info("Git clone")
    "git clone --progress -v https://github.com/stanfordnlp/CoreNLP.git" ! procLog

    log.info("Checkout specific commit (latest release)")
    Process("git"::"checkout"::s"$commitFor3_9_1"::Nil, coreNLPdirectory) ! procLog

    log.info("mvnw download")
    url("https://raw.githubusercontent.com/takari/maven-wrapper/master/mvnw") #> {coreNLPdirectory / "mvnw"} ! procLog
    val prop: File = {
      coreNLPdirectory / ".mvn/wrapper/maven-wrapper.properties"
    }
    prop.getParentFile.mkdirs()
    url("https://raw.githubusercontent.com/takari/maven-wrapper/master/.mvn/wrapper/maven-wrapper.properties") #> prop ! procLog
  }
  log.info("Modify pom.xml")
  val pom = IO.read(coreNLPdirectory / "pom.xml")
    .replaceAll("(?s)<plugins>.*</plugins>","<plugins></plugins>")
    .replace("<artifactId>stanford-corenlp</artifactId>",
      "<artifactId>stanford-corenlp-shaded</artifactId>")
  val plugin = IO.read(baseDirectory.value / "src/stanfordcorenlp/pom_shading.xml")

  IO.write(coreNLPdirectory / "pom.xml",pom.replace("<plugins>","<plugins>"+plugin))

  log.info("Create shaded jar")
  Process("sh"::"mvnw"
    ::"package"::"-Dmaven.test.skip=true"::Nil,
    coreNLPdirectory) ! procLog
}

dependencyLib := {
  val log = streams.value.log
  def copyJar(jar: File, to:File):Unit = {
    log.info(s"Copy $jar to $to")
    IO.copyFile(jar,new File(to,jar.getName))
  }

  (update in Compile).value
    .select(
      configuration = configurationFilter("compile"),
      module = moduleFilter(),artifact = artifactFilter())
    .foreach(copyJar(_, file(s"$appsPath/lib_managed")))
}


coreNLPmodels := {
  val log = streams.value.log
  def copyJar(jar: File, to:File):Unit = {
    log.info(s"Copy $jar to $to")
    IO.copyFile(jar,new File(to,jar.getName))
  }

  (update in Compile).value
    .select(
      configuration = configurationFilter("provided"),
      module = moduleFilter(),artifact = artifactFilter(name="stanford-corenlp"))
    .foreach(copyJar(_, file(s"$appsPath/corenlp")))
}
