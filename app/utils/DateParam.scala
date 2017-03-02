package utils

import scala.util.matching.Regex

/** not Y10K ready, I am afraid... */
object DateParam {
  val EUR_DATE_PATTERN: Regex = """(\d\d)\.(\d\d)\.(\d\d\d\d)""".r

  def eurToIso( date: String ): String = {
    date match {
      case EUR_DATE_PATTERN( day, month, year ) =>
        year+month+day
    }
  }

  def isoToEur( date: String ): String = {
    require( date.length() == 8, "argument must be exactly 8 characters" )

    date.slice(6,8)+"."+date.slice(4,6)+"."+date.slice(0,4)
  }

}
