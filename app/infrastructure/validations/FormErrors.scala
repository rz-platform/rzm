package infrastructure.validations

import play.api.data.{ Form, FormError }

object FormErrors {
  def error[A](form: Form[A], err: FormError): Form[A] =
    form.copy(
      errors = form.errors ++ Seq(err)
    )
}
