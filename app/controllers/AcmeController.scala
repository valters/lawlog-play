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
import io.github.valters.acme.KeyStorageUtil
import io.github.valters.acme.KeyStorage
import com.typesafe.scalalogging.Logger
import scala.util.Success
import scala.util.Failure

/**
 * Handles HTTP certificate provisioning and renewal
 */
@Singleton
class AcmeController @Inject() ( wsClient: WSClient ) extends Controller {
  private val logger = Logger[AcmeController]

  val Keys = new KeyStorage( KeyStorage.Defaults )

  val keyAuthHandle = new AtomicReference[String](null)

  /** test env URL */
  val LetsEncryptStaging = "https://acme-staging.api.letsencrypt.org"

  val TestDomain: String = "v1.test.vingolds.ch"

  val TestDomainIdent = AcmeProtocol.AcmeIdentifier( value = TestDomain )

  val HttpClient = new AcmeHttpClient( wsClient )

  def cert = Action {

    val acmeRegistration = Promise[AcmeProtocol.SimpleRegistrationResponse]()
    val acmeAgreement = Promise[AcmeProtocol.RegistrationResponse]()
    val acmeChallenge = Promise[AcmeProtocol.AuthorizationResponse]()
    val acmeChallengeDetails = Promise[AcmeProtocol.ChallengeHttp]()
    val afterChallengeDetails = Promise[AcmeProtocol.ChallengeHttp]()
    val certificate = Promise[X509Certificate]()

    // when successfully retrieved directory, notify that AcmeServer promise is now available
    val directory = LetsEncryptStaging + AcmeProtocol.DirectoryFragment
    HttpClient.getDirectory( directory ).onSuccess{ case d: AcmeProtocol.Directory =>
      HttpClient.acmeServer.success( new AcmeProtocol.AcmeServer( directory, d ) )
    }

    getInitialAccount( acmeRegistration )

    agree( acmeRegistration.future, acmeAgreement )

    getAuthorizedAccount( acmeAgreement.future, acmeChallenge )

    startChallenge( acmeChallenge.future, acmeChallengeDetails )

    logger.debug("+awaiting CHAL end" )
    Await.result( acmeChallengeDetails.future, new DurationInt(40).seconds )

    logger.info("+revisit details (after sleeping 3 seconds)" )
    Thread.sleep( 3000L )

    finishChallenge( acmeChallengeDetails.future, afterChallengeDetails )

    Await.result( afterChallengeDetails.future, new DurationInt(10).seconds )

    issueCertificate( afterChallengeDetails.future )

    logger.debug("+ending" )

    Ok( "certified" )
  }

  /** provides response to the .well-known/acme-challenge/ request */
  def challenge( token: String ) = Action {
     Option( keyAuthHandle.get() ) match {
       case None => {
         logger.warn( "Unrecognized token {}, providing diagnostic response", token )
         NotFound( s"""{ "token": "$token" }""" )
       }
       case Some(key) => {
         logger.warn( "ACME server came by, providing successful response to challenge {}", token )
         Ok( key )
       }
    }
  }

  /** register (or retrieve existing) ACME server account */
  private def getInitialAccount( registration: Promise[AcmeProtocol.SimpleRegistrationResponse] ) = {

    val futureReg: Future[AcmeProtocol.SimpleRegistrationResponse] = HttpClient.acmeServer.future.flatMap{ server: AcmeProtocol.AcmeServer => {
      logger.debug("+ server received" )
      val req = new AcmeProtocol.RegistrationRequest( Array( "mailto:cert-admin@example.com" ) )
      val nonce = HttpClient.getNonce()
      logger.debug("++ dir nonce: {}", nonce )
      val jwsReq = AcmeJson.encodeRequest( req, nonce, Keys.userKey )
      HttpClient.registration( server.newReg, jwsReq.toString() )
    } }
    // after we retrieved registration, we notify that registration response is available
    futureReg.onSuccess{ case response: AcmeProtocol.SimpleRegistrationResponse =>
      logger.debug("resp: {}", response)
      registration.success( response ) }
  }

  /** check if reg.agreement Terms of Service URL is provided: we need to indicate we accept it. or otherwise proceed directly to next step */
  private def agree( newReg: Future[AcmeProtocol.SimpleRegistrationResponse], agreement: Promise[AcmeProtocol.RegistrationResponse] ) = {

    val futureAgree: Future[AcmeProtocol.RegistrationResponse] = newReg.flatMap {
      reg: AcmeProtocol.SimpleRegistrationResponse => {
        reg.agreement match {
          case None => Future.successful( AcmeProtocol.RegistrationResponse() ) // no registration needed
          case agreement =>
              logger.info("+ start ToS agree {}", agreement )
              val req = new AcmeProtocol.RegistrationRequest( resource = AcmeProtocol.reg, agreement = agreement )
              val nonce = HttpClient.getNonce()
              logger.debug("++ new-reg nonce: {}", nonce )
              val jwsReq = AcmeJson.encodeRequest( req, nonce, Keys.userKey )
              HttpClient.agreement( reg.uri, jwsReq.toString() )
        }
      }
    }

    // after we retrieved agreement, notify that final registration response is available
    futureAgree.onSuccess{ case response: AcmeProtocol.RegistrationResponse =>
      agreement.success( response ) }
  }

  private def getAuthorizedAccount( getAgreedReg: Future[AcmeProtocol.RegistrationResponse], challenge: Promise[AcmeProtocol.AuthorizationResponse] ) = {
    val futureAuth: Future[AcmeProtocol.AuthorizationResponse] = getAgreedReg.flatMap{ _ => {

      logger.debug("+ start authz" )
      val nonce = HttpClient.getNonce()
      logger.debug("++ reg-agree nonce: {}", nonce )

      val req = new AcmeProtocol.AuthorizationRequest( identifier = TestDomainIdent )
      val jwsReq = AcmeJson.encodeRequest( req, nonce, Keys.userKey )

      HttpClient.acmeServer.future.value.map {
        case Success(server) => HttpClient.authorize( server.newAuthz, jwsReq.toString() )
        case Failure(e) =>
          logger.error( "Server did not show up: {}", e,e )
          Future.failed(e)
      }
      .get
    } }
    // after authorization is done
    futureAuth.onSuccess{ case response: AcmeProtocol.AuthorizationResponse =>
      challenge.success( response ) }
  }

  def startChallenge( getChallenges: Future[AcmeProtocol.AuthorizationResponse], challengeDetails: Promise[AcmeProtocol.ChallengeHttp] ) = {

    val futureChallenge: Future[AcmeProtocol.ChallengeHttp] = getChallenges.flatMap{ authz: AcmeProtocol.AuthorizationResponse => {

      logger.debug("+ start accept http-01 challenge" )
      val httpChallenge = AcmeJson.findHttpChallenge( authz.challenges ).get

      val nonce = HttpClient.getNonce()
      logger.debug("++ authz nonce: {}", nonce )

      val keyAuth = AcmeJson.withThumbprint( httpChallenge.token, Keys.userKey )
      keyAuthHandle.set( keyAuth ) // shared resource
      val req = AcmeProtocol.AcceptChallengeHttp( keyAuthorization = keyAuth )
      val jwsReq = AcmeJson.encodeRequest( req, nonce, Keys.userKey )

      HttpClient.acmeServer.future.value.map {
        case Success(server) => HttpClient.challenge( httpChallenge.uri, jwsReq.toString() )
        case Failure(e) =>
          logger.error( "Server did not show up: {}", e, e )
          Future.failed(e)
      }
      .get

    } }
    // after challenge is accepted
    futureChallenge.onSuccess{ case response: AcmeProtocol.ChallengeHttp =>
      challengeDetails.success( response ) }
  }

  def finishChallenge( getChallengeDetails: Future[AcmeProtocol.ChallengeHttp], afterChallengeDetails: Promise[AcmeProtocol.ChallengeHttp] ) = {
    val afterChallenge: Future[AcmeProtocol.ChallengeType] = getChallengeDetails.flatMap{ challenge: AcmeProtocol.ChallengeHttp => {

      HttpClient.challengeDetails( challenge.uri )
    } }
    afterChallenge.onSuccess{ case response: AcmeProtocol.ChallengeHttp =>
      logger.info( "Details parsed: {}", response )
      afterChallengeDetails.success( response )
    }
  }

  def issueCertificate( getAfterChallenge: Future[AcmeProtocol.ChallengeHttp] ) = {
    val issueCertificate: Future[X509Certificate] = getAfterChallenge.flatMap{ challenge: AcmeProtocol.ChallengeHttp => {

      val server = HttpClient.acmeServer.future.value.get.get

      val nonce = HttpClient.getNonce()
      logger.debug("++ challenge nonce: {}", nonce )

      val csr = Keys.generateCertificateSigningRequest( TestDomain )
      val req = AcmeProtocol.CertificateRequest( csr = KeyStorageUtil.asBase64( csr ) )
      val jwsReq = AcmeJson.encodeRequest( req, nonce, Keys.userKey )

      HttpClient.issue( server.newCert, jwsReq.toString() )
    } }
    issueCertificate.onSuccess{ case cert: X509Certificate =>
      logger.info("saving certificate")
      Keys.updateKeyStore( cert )
    }
  }

}
