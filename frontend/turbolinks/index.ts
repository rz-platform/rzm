import namespace from "./namespace"

export default namespace

if (!window.Turbolinks) {
  window.Turbolinks = namespace
  namespace.start()
}
