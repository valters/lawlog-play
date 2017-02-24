package io.github.valters.acme

import com.typesafe.scalalogging.Logger
import java.security.cert.X509Certificate
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.security.cert.Certificate

object KeyStorage {
  /** we like to hardcode some sensible defaults - but allow you to override if wanted */
  case class Params( DomainCertAlias: String, DomainCertFile: String, ChainCertAlias: String, KeystorePassword: String, AppKeystore: String,
      val UserKey: String, val DomainKey: String )

  val Defaults = Params( DomainCertAlias = "domain",
      DomainCertFile =  "domain.crt",
      ChainCertAlias = "ca-root",
      KeystorePassword = getPropertyOrDefault( "play.server.https.keyStore.password", "changeit" ),
      AppKeystore = getPropertyOrDefault( "play.server.https.keyStore.path", "conf/play-app.keystore" ),
      UserKey = "user.key",
      DomainKey = "domain.key" )

    def getPropertyOrDefault( propertyName: String, defaultValue: String ) = {
      val prop = Option( System.getProperty( propertyName ) )
      prop match {
        case None => defaultValue
        case Some(propValue) => propValue
      }
    }

}

class KeyStorage( params: KeyStorage.Params ) {
    private val logger = Logger[KeyStorage]

    val userKey = KeyStorageUtil.getUserKey( params.UserKey )
    val domainKey = KeyStorageUtil.getDomainKey( params.DomainKey )

  def generateCertificateSigningRequest( domain: String ): PKCS10CertificationRequest = {
      KeyStorageUtil.generateCertificateSigningRequest( domainKey.toKeyPair(), domain )
  }

  def generateKeyStore(cert: X509Certificate) = {
    val ks = KeyStorageUtil.newKeystore

    ks.setCertificateEntry( params.DomainCertAlias, cert );
    KeyStorageUtil.storeCertificateKey( ks, params.KeystorePassword, cert, domainKey.toKeyPair(), params.DomainKey );

    val chain = KeyStorageUtil.getIntermediateChain( cert )
    if( chain.isPresent() ) {
      ks.setCertificateEntry( params.ChainCertAlias, chain.get )
    }

    KeyStorageUtil.saveKeystore( ks, params.AppKeystore, params.KeystorePassword );
    logger.info("wrote keystore: {}", params.AppKeystore )
  }

    /*
    public static void storeCertificate( final Certificate cert ) {
        try {
            final KeyStore keystore = loadKeystore( APP_KEYSTORE, KEYSTORE_PASSWORD );
            keystore.setCertificateEntry( DOMAIN_CERT_ALIAS, cert );
            saveKeystore( keystore, APP_KEYSTORE, KEYSTORE_PASSWORD );
        }
        catch( final Exception e ) {
            throw new RuntimeException( "Failed to store certificate into app keystore[" + APP_KEYSTORE + "]", e );
        }
    }

*/
}
