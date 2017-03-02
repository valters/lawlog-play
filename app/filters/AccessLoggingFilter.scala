package filters

import javax.inject.Inject
import akka.stream.Materializer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.Logger
import play.api.mvc._
import play.api._

class AccessLoggingFilter @Inject() (implicit val mat: Materializer) extends Filter {

  val accessLogger = Logger("access")

  def apply(next: (RequestHeader) => Future[Result])(request: RequestHeader): Future[Result] = {
    val resultFuture = next(request)

    resultFuture.foreach(result => {
      val msg = s"method=${request.method} uri=${request.uri} remote-address=${request.remoteAddress} status=${result.header.status}"
      accessLogger.info(msg)
    })

    resultFuture
  }
}
