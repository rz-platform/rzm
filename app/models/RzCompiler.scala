package models

sealed trait RzCompiler {
  def id: String // unique compiler identifier
  def name: String
}

case object PdfLatex extends RzCompiler {
  val id   = "pdflatex"
  val name = "pdfLaTeX"
}
case object XeLatex extends RzCompiler {
  val id   = "xelatex"
  val name = "XeLaTeX"
}

case object LuaLatex extends RzCompiler {
  val id   = "lualatex"
  val name = "LuaLaTeX"
}

object RzCompiler {
  def make(id: String): Option[RzCompiler] =
    id match {
      case PdfLatex.id => Some(PdfLatex)
      case XeLatex.id  => Some(XeLatex)
      case LuaLatex.id => Some(LuaLatex)
      case _           => Option.empty[RzCompiler]
    }
  def make(id: Option[String]): Option[RzCompiler] =
    id match {
      case Some(s) => make(s)
      case _       => Option.empty[RzCompiler]
    }
}

sealed trait RzBib {
  def id: String // unique bibliography management package identifier
}

case object BibTex extends RzBib {
  val id = "bibtex"
}

case object NatBib extends RzBib {
  val id = "natbib"
}

case object BibLatex extends RzBib {
  val id = "biblatex"
}

object RzBib {
  def make(id: String): Option[RzBib] =
    id match {
      case BibTex.id   => Some(BibTex)
      case NatBib.id   => Some(NatBib)
      case BibLatex.id => Some(BibLatex)
      case _           => Option.empty[RzBib]
    }
  def make(id: Option[String]): Option[RzBib] =
    id match {
      case Some(s) => make(s)
      case _       => Option.empty[RzBib]
    }
}
