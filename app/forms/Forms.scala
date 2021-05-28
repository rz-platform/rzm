package forms

import play.api.data.{ Form, FormError }

object Forms {
  def error(form: Form[Any], err: FormError): Form[Any] =
    form.copy(
      errors = form.errors ++ Seq(err)
    )
}
