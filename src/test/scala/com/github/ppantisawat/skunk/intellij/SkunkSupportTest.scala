package com.github.ppantisawat.skunk.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.components.libextensions.LibraryExtensionsManager
import org.jetbrains.plugins.scala.lang.macros.evaluator.ScalaMacroExpandable
import org.junit.Assert.{assertFalse, assertNotNull, fail}

import java.nio.file.Paths
import java.util.Collections
import scala.util.control.NonFatal

class SkunkSupportTest extends BasePlatformTestCase {
  override protected def setUp(): Unit = {
    super.setUp()
    addScalaLibraries()
    registerSkunkExtension()
  }

  def testSqlInterpolationResolvesQuery(): Unit = {
    myFixture.configureByText("SkunkUsage.scala", skunkStubs + scala2Usage)

    assertQueryResolves()
  }

  def testSqlInterpolationResolvesQueryWithMultipleArguments(): Unit = {
    myFixture.configureByText("SkunkUsage.scala", skunkStubs + scala2MultipleArgumentUsage)

    assertQueryResolves()
  }

  private def assertQueryResolves(): Unit = {
    val queryOffset: Int = myFixture.getFile.getText.indexOf(".query") + 1
    val queryReference = myFixture.getFile.findReferenceAt(queryOffset)

    assertNotNull("query reference should exist", queryReference)
    assertNotNull("query should resolve after Skunk macro expansion", queryReference.resolve())
  }

  private def registerSkunkExtension(): Unit = {
    val extensionJar = Paths.get(sys.props("skunk.intellij.test.extensionJar"))
    val manager = LibraryExtensionsManager.getInstance(getProject)
    try {
      manager.addExtension(extensionJar)
    } catch {
      case NonFatal(exception) if Option(exception.getMessage).exists(_.contains("already loaded")) =>
      case NonFatal(exception) =>
        fail(s"Failed to register Skunk extension jar $extensionJar: ${exception.getMessage}")
    }
    assertFalse(
      "Skunk macro extension should load",
      manager.getExtensions[ScalaMacroExpandable].isEmpty
    )
  }

  private def addScalaLibraries(): Unit =
    ApplicationManager.getApplication.runWriteAction(new Runnable {
      override def run(): Unit = {
        val libraryRoots: java.util.List[String] = Collections.unmodifiableList(
          java.util.Arrays.asList(
            jarRoot(classOf[scala.Option[_]]),
            jarRoot(classOf[scala.reflect.macros.whitebox.Context])
          )
        )
        ModuleRootModificationUtil.addModuleLibrary(
          myFixture.getModule,
          "scala-2.13",
          libraryRoots,
          Collections.emptyList()
        )
      }
    })

  private def jarRoot(clazz: Class[_]): String =
    s"jar://${PathUtil.getJarPathForClass(clazz)}!/"

  private val scala2Usage: String =
    """
      |package example
      |
      |import skunk._
      |import skunk.codec.all._
      |import skunk.syntax.all._
      |
      |object SkunkUsage {
      |  val id: Encoder[Int] = int4
      |  val q = sql"select name from person where id = $id".query(varchar)
      |}
      |""".stripMargin

  private val scala2MultipleArgumentUsage: String =
    """
      |package example
      |
      |import skunk._
      |import skunk.codec.all._
      |import skunk.syntax.all._
      |
      |object SkunkUsage {
      |  val id: Encoder[Int] = int4
      |  val name: Encoder[String] = varcharCodec
      |  val q = sql"select name from person where id = $id and name = $name".query(varchar)
      |}
      |""".stripMargin

  private val skunkStubs: String =
    """
      |package skunk
      |
      |trait Encoder[A] {
      |  def *:[B](left: Encoder[B]): Encoder[(B, A)] = ???
      |  def ~[B](right: Encoder[B]): Encoder[(A, B)] = ???
      |}
      |
      |trait Decoder[A]
      |trait Codec[A] extends Encoder[A] with Decoder[A]
      |
      |trait Void
      |object Void extends Void {
      |  val codec: Codec[Void] = ???
      |}
      |
      |final class Fragment[A](val encoder: Encoder[A]) {
      |  def query[B](decoder: Decoder[B]): Query[A, B] = ???
      |  def command: Command[A] = ???
      |}
      |
      |object Fragment {
      |  def apply[A](parts: List[Any], encoder: Encoder[A], origin: util.Origin): Fragment[A] =
      |    new Fragment[A](encoder)
      |}
      |
      |trait Query[A, B]
      |trait Command[A]
      |
      |package util {
      |  final case class Origin(file: String, line: Int)
      |  object Origin {
      |    val unknown: Origin = Origin("<unknown>", 0)
      |  }
      |}
      |
      |package codec {
      |  object all {
      |    val int4: skunk.Encoder[Int] = ???
      |    val varchar: skunk.Decoder[String] = ???
      |    val varcharCodec: skunk.Codec[String] = ???
      |  }
      |}
      |
      |package syntax {
      |  import scala.reflect.macros.whitebox
      |
      |  class StringContextOps private[skunk](sc: StringContext) {
      |    def sql(argSeq: Any*): Any =
      |      macro StringContextOps.sql_impl
      |  }
      |
      |  object StringContextOps {
      |    def sql_impl(c: whitebox.Context)(argSeq: c.Tree*): c.Tree = ???
      |  }
      |
      |  trait ToStringContextOps {
      |    implicit def toStringOps(sc: StringContext): StringContextOps =
      |      new StringContextOps(sc)
      |  }
      |
      |  object all extends ToStringContextOps
      |}
      |""".stripMargin
}
