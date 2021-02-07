# Secure session management

Play has a simple session cookie that is signed but not encrypted. RZM securely stores information in a client-side cookie without revealing it to the browser by encrypting the data with libsodium.

The only server-side state is a mapping of session ids to secret keys.  When the user logs out, the mapping is deleted, and the encrypted information cannot be retrieved using the client's session id. This prevents replay attacks after logout, even if the user saves off the cookies and replays them with the same browser and IP address.

Install libsodium before using (non-Java binary install)

On MacOS:

```bash
brew install libsodium
```

On Ubuntu:

```bash
apt-get install libsodium-dev
```

On Fedora:

```bash
dnf install libsodium-devel
```

On CentOS:

```bash
yum install libsodium-devel
```


## Encryption

Encryption is handled by `encryption.EncryptionService`.  It uses secret key authenticated encryption with [Kalium](https://github.com/abstractj/kalium/), a thin Java wrapper around libsodium.  Kalium's `SecretBox` is an object oriented mapping to libsodium's `crypto_secretbox_easy` and `crypto_secretbox_open_easy`, described [here](https://download.libsodium.org/doc/secret-key_cryptography/authenticated_encryption.html).  The underlying stream cipher is XSalsa20, used with a Poly1305 MAC.

An abstract [cookie baker](https://www.playframework.com/documentation/latest/api/scala/index.html#play.api.mvc.CookieBaker), `EncryptedCookieBaker` is used to serialize and deserialize encrypted text between a `Map[String, String]` and a case class representation.  `EncryptedCookieBaker` also extends the `JWTCookieDataCodec` trait, which handles the encoding between `Map[String, String]` and the raw string data written out in the HTTP response in [JWT format](https://tools.ietf.org/html/rfc7519).

A factory `UserInfoCookieBakerFactory` creates a `UserInfoCookieBaker` that uses the session specific secret key to map a `UserInfo` case class to and from a cookie.

Then finally, an `AuthenticatedAction`, an action builder, handles the work of reading in a `UserInfo` from a cookie and attaches it to an `AccountRequest`, a [wrapped request](https://www.playframework.com/documentation/latest/ScalaActionsComposition) so that the controllers can work with `UserInfo` without involving themselves with the underlying logic.


### Session Store

RZM uses Redis as a session store. Redis is extremely fast at retrieving simple values.
