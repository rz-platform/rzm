package filters

import play.api.Logger
import play.api.http.Status.PERMANENT_REDIRECT
import play.api.mvc.{ EssentialAction, EssentialFilter, RequestHeader, Results }

import javax.inject.Singleton

@Singleton
class TrailingSlashFilter extends EssentialFilter {

  private val logger = Logger(getClass)

  override def apply(next: EssentialAction): EssentialAction = EssentialAction { req: RequestHeader =>
    import play.api.libs.streams.Accumulator
    if (req.path.endsWith("/") && req.path != "/") {
      Accumulator.done(Results.Redirect(getPath(req), PERMANENT_REDIRECT))
    } else {
      next(req)
    }
  }

  protected def getPath(req: RequestHeader): String =
    if (req.rawQueryString.isEmpty) {
      req.path.stripSuffix("/")
    } else {
      s"${req.path.stripSuffix("/")}?${req.rawQueryString}"
    }
}
