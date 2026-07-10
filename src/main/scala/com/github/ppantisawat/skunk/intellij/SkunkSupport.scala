package com.github.ppantisawat.skunk.intellij

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroImpl, MacroInvocationContext, ScalaMacroExpandable}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScExistentialArgument, ScExistentialType, ScParameterizedType, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.project.ProjectContext

class SkunkSupport extends ScalaMacroExpandable {
  override val boundMacro: Seq[MacroImpl] =
    Seq(MacroImpl("sql", "skunk.syntax.StringContextOps"))

  override def expandMacro(macros: ScFunction, context: MacroInvocationContext): Option[ScExpression] = {
    implicit val typeContext: Context = Context(context.call)
    val scope: ElementScope = context.call.elementScope

    for {
      encoderClass <- scope.getCachedClass("skunk.Encoder")
      encoderType = wildcardType(encoderClass)
      fragmentClass <- scope.getCachedClass("skunk.Fragment")
      fragmentType = wildcardType(fragmentClass)
      voidClass <- scope.getCachedClass("skunk.Void")
      voidFragmentType = ScParameterizedType(
        ScDesignatorType(fragmentClass),
        List(ScDesignatorType(voidClass))
      )
      argumentEncoders = context.call.argumentExpressions.flatMap { argument: ScExpression =>
        argument.`type`().toOption.flatMap { argumentType: ScType =>
          argumentEncoder(argument, argumentType, encoderType, fragmentType, voidFragmentType)
        }
      }
      encoder = combineEncoders(argumentEncoders)
    } yield {
      ScalaPsiElementFactory.createExpressionWithContextFromText(
        fragmentExpression(encoder),
        context.call,
        null
      )
    }
  }

  private def argumentEncoder(
    expression: ScExpression,
    scType: ScType,
    encoderType: ScType,
    fragmentType: ScType,
    voidFragmentType: ScType
  )(implicit context: Context): Option[String] =
    if (scType.conforms(encoderType)) {
      Some(expression.getText)
    } else if (scType.conforms(voidFragmentType)) {
      None
    } else if (scType.conforms(fragmentType)) {
      Some(s"${expression.getText}.encoder")
    } else {
      None
    }

  private def combineEncoders(encoders: Seq[String]): String =
    encoders
      .reduceRightOption((encoder: String, accumulator: String) => s"$encoder *: $accumulator")
      .getOrElse("_root_.skunk.Void.codec")

  private def fragmentExpression(encoder: String): String =
    s"_root_.skunk.Fragment(List.empty, $encoder, _root_.skunk.util.Origin.unknown)"

  private def wildcardType(psiClass: PsiClass): ScType = {
    implicit val context: ProjectContext = psiClass
    ScExistentialType(
      ScParameterizedType(
        ScDesignatorType(psiClass),
        List(ScExistentialArgument("_$1", List.empty, Nothing, Any))
      )
    )
  }
}
