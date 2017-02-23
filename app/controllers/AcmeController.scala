package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import services.TableOfContents
import play.twirl.api.Html
import play.api.libs.ws.WSClient
import io.github.valters.acme.AcmeJson
import io.github.valters.acme.AcmeProtocol
import io.github.valters.acme.AcmeHttpClient
import scala.concurrent.duration.`package`.DurationInt
import scala.concurrent.Await
import scala.concurrent.Promise
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.atomic.AtomicReference
import java.security.cert.X509Certificate
import io.github.valters.acme.KeyStorage

/**
 * Handles HTTP certificate provisioning and renewal
 */
@Singleton
class AcmeController @Inject() ( wsClient: WSClient ) extends Controller {

  val keypair = KeyStorage.getUserKey()
  val domainKey = KeyStorage.getDomainKey()

  val keyAuthHandle = new AtomicReference[String](null)

  /** test env URL */
  val LetsEncryptStaging = "https://acme-staging.api.letsencrypt.org"

  val TestDomain: String = "v1.test.vingolds.ch"

  val TestDomainIdent = AcmeProtocol.AcmeIdentifier( value = TestDomain )

  val HttpClient = new AcmeHttpClient( wsClient )

  def cert = Action {

    val acmeServer = Promise[AcmeProtocol.AcmeServer]()
    val acmeRegistration = Promise[AcmeProtocol.SimpleRegistrationResponse]()
    val acmeAgreement = Promise[AcmeProtocol.RegistrationResponse]()
    val acmeChallenge = Promise[AcmeProtocol.AuthorizationResponse]()
    val acmeChallengeDetails = Promise[AcmeProtocol.ChallengeHttp]()
    val afterChallengeDetails = Promise[AcmeProtocol.ChallengeHttp]()
    val certificate = Promise[X509Certificate]()

    // when successfully retrieved directory, notify that AcmeServer promise is now available
    HttpClient.getDirectory( LetsEncryptStaging ).onSuccess{ case d: AcmeProtocol.Directory =>
      acmeServer.success( new AcmeProtocol.AcmeServer( d ) )
    }

    val getServer = acmeServer.future
    val futureReg: Future[AcmeProtocol.SimpleRegistrationResponse] = getServer.flatMap{ server: AcmeProtocol.AcmeServer => {
      println("+ server received" )
      val req = new AcmeProtocol.RegistrationRequest( Array( "mailto:cert-admin@example.com" ) )
      val nonce = HttpClient.getNonce()
      println("++ dir nonce: " + nonce )
      val jwsReq = AcmeJson.encodeRequest( req, nonce, keypair )
      HttpClient.registration( server.newReg, jwsReq.toString() )
    } }
    // after we retrieved registration, we notify that registration response is available
    futureReg.onSuccess{ case response: AcmeProtocol.SimpleRegistrationResponse =>
      println(response)
      acmeRegistration.success( response ) }

    val getNewReg = acmeRegistration.future
    val futureAgree: Future[AcmeProtocol.RegistrationResponse] = getNewReg.flatMap{ reg: AcmeProtocol.SimpleRegistrationResponse => {

      val req = new AcmeProtocol.RegistrationRequest( resource = AcmeProtocol.reg, agreement = Some( reg.agreement ) )
      val nonce = HttpClient.getNonce()
      println("++ new-reg nonce: " + nonce )
      val jwsReq = AcmeJson.encodeRequest( req, nonce, keypair )
      HttpClient.agreement( reg.uri, jwsReq.toString() )

    } }
    // after we retrieved agreement, notify that final registration response is available
    futureAgree.onSuccess{ case response: AcmeProtocol.RegistrationResponse =>
      acmeAgreement.success( response ) }

    val getAgreedReg = acmeAgreement.future
    val futureAuth: Future[AcmeProtocol.AuthorizationResponse] = getAgreedReg.flatMap{ reg: AcmeProtocol.RegistrationResponse => {

      println("+ start ToS agreement" )
      val nonce = HttpClient.getNonce()
      println("++ reg-agree nonce: " + nonce )

      val req = new AcmeProtocol.AuthorizationRequest( identifier = TestDomainIdent )
      val jwsReq = AcmeJson.encodeRequest( req, nonce, keypair )

      val server = getServer.value.get.get

      HttpClient.authorize( server.newAuthz, jwsReq.toString() )
    } }
    // after authorization is done
    futureAuth.onSuccess{ case response: AcmeProtocol.AuthorizationResponse =>
      acmeChallenge.success( response ) }

    val getChallenges = acmeChallenge.future
    val futureChallenge: Future[AcmeProtocol.ChallengeHttp] = getChallenges.flatMap{ authz: AcmeProtocol.AuthorizationResponse => {

      println("+ start accept http-01 challenge" )
      val httpChallenge = AcmeJson.findHttpChallenge( authz.challenges ).get

      val nonce = HttpClient.getNonce()
      println("++ authz nonce: " + nonce )

      val keyAuth = AcmeJson.withThumbprint( httpChallenge.token, keypair )
      keyAuthHandle.set( keyAuth ) // shared resource
      val req = AcmeProtocol.AcceptChallengeHttp( keyAuthorization = keyAuth )
      val jwsReq = AcmeJson.encodeRequest( req, nonce, keypair )

      val server = getServer.value.get.get

      HttpClient.challenge( httpChallenge.uri, jwsReq.toString() )
    } }
    // after challenge is accepted
    futureChallenge.onSuccess{ case response: AcmeProtocol.ChallengeHttp =>
      acmeChallengeDetails.success( response ) }


    println("+awaiting CHAL end" )
    Await.result( futureChallenge, new DurationInt(40).seconds )

    println("+revisit details (after sleeping 3 seconds)" )
    Thread.sleep( 3000L )

    val getChallengeDetails = acmeChallengeDetails.future
    val afterChallenge: Future[AcmeProtocol.ChallengeType] = getChallengeDetails.flatMap{ challenge: AcmeProtocol.ChallengeHttp => {

      HttpClient.challengeDetails( challenge.uri )
    } }
    afterChallenge.onSuccess{ case response: AcmeProtocol.ChallengeHttp =>
      println( s"Details parsed: $response" )
      afterChallengeDetails.success( response )
    }

    Await.result( afterChallenge, new DurationInt(10).seconds )
    val getAfterChallenge = afterChallengeDetails.future
    val issueCertificate: Future[X509Certificate] = getAfterChallenge.flatMap{ challenge: AcmeProtocol.ChallengeHttp => {

      val server = getServer.value.get.get

      val nonce = HttpClient.getNonce()
      println("++ challenge nonce: " + nonce )

      val csr = KeyStorage.generateCertificateSigningRequest( domainKey.toKeyPair, TestDomain )
      val req = AcmeProtocol.CertificateRequest( csr = KeyStorage.asBase64( csr ) )
      val jwsReq = AcmeJson.encodeRequest( req, nonce, keypair )

      HttpClient.issue( server.newCert, jwsReq.toString() )
    } }



    println("+ending" )

    Ok( "certified" )
  }

  /** provides response to the .well-known/acme-challenge/ request */
  def challenge( token: String ) = Action {
     Option( keyAuthHandle.get() ) match {
       case None => Ok( s"""{ "token": "$token" }""" )
       case Some(key) => Ok( key )
    }
  }

}
