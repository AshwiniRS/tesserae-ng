package org.tesserae.lexicon.lookup

import org.iq80.leveldb._
import org.fusesource.leveldbjni.JniDBFactory._
import java.io._
import org.slf4j.LoggerFactory
import lex.db.CSVLine
import collection.mutable.{Set => MutableSet, HashSet => MutableHashSet}

/**
 * How to normalize the keys
 */
sealed trait NormalizeLevel {
  def name: String
}

/**
 * No normalization should be performed
 */
object NoNormalization extends NormalizeLevel {
  def name: String = "none"
}

/**
 * Do some normalization
 */
object PartialNormalization extends NormalizeLevel {
  def name: String = "partial"
}

/**
 * Do full normalization
 */
object FullNormalization extends NormalizeLevel {
  def name: String = "full"
}

object Main {

  private lazy val logger = LoggerFactory.getLogger("Main")

  /**
   * Print out the usage and exit
   * @param code The exit code (0 means success)
   */
  private def usage(code: Int) {
    val out = if (code == 0) { System.out } else { System.err }
    out.println("Usage: java -jar lexicon-ingest.jar [OPTIONS]...")
    out.println()
    out.println("Options:")
    out.println("  -d, --db=DIR             The location to the lexicon database (required)")
    out.println("  -t, --token=STRING       A token to look up (required)")
    out.println("  -k, --key-norm=LEVEL     Normalize keys (optional, one of 'none', 'partial',")
    out.println("                           or 'full'). Default is 'full'")
    out.println("  -h, --help               Show this helpful message and exit")
    System.exit(code)
  }

  /**
   * Parse the command line options
   *
   * @param args The command line arguments
   * @return A tuple of (database directory, lookup token, normalization level)
   */
  private def parseArgs(args: Array[String]) = {
    import gnu.getopt.{Getopt, LongOpt}

    val longopts = Array(
      new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h'),
      new LongOpt("db", LongOpt.REQUIRED_ARGUMENT, null, 'd'),
      new LongOpt("token", LongOpt.REQUIRED_ARGUMENT, null, 't'),
      new LongOpt("key-norm", LongOpt.REQUIRED_ARGUMENT, null, 'k')
    )

    var c = -1
    var db: File = null
    var token: String = null
    var normalizeKeys: NormalizeLevel = FullNormalization
    val g = new Getopt("lexicon-ingest", args, "hd:t:k:", longopts)

    c = g.getopt()
    while(c != -1) {
      c match {
        case 'h' => usage(0)
        case 'd' => db = new File(g.getOptarg)
        case 't' => token = g.getOptarg
        case 'k' => {
          normalizeKeys = g.getOptarg match {
            case "none" => NoNormalization
            case "partial" => PartialNormalization
            case "full" => FullNormalization
            case other => {
              System.err.println("Error: unknown key-norm level: `" + other + "'")
              System.exit(1); NoNormalization
            }
          }
        }
        case o => {
          System.err.println("Error: unhandled option: `" + o + "'")
          System.exit(1)
        }
      }
      c = g.getopt()
    }

    if (db == null || token == null) {
      usage(1)
    }

    (db, token, normalizeKeys)
  }

  /**
   * Use a leveldb database
   *
   * @param file The location of the database directory
   * @param callback A callback that will use the database
   * @tparam A Some arbitrary return type
   * @return Whatever the callback returns
   */
  private def usingDatabase[A](file: File)(callback: DB => A): A = {
    val opts = new Options
    opts.createIfMissing(true)
    opts.compressionType(CompressionType.NONE)
    val db = factory.open(file, opts)
    try {
      callback(db)
    } finally {
      db.close()
    }
  }

  /**
   * Get the plural version of a string
   *
   * @param i An integer
   * @param singular The singular version of a word
   * @param plural The plural version of a word
   * @return A plural string for the given count
   */
  private def plural(i: Int, singular: String, plural: String): String =
    i match {
      case 1 => "1 " + singular
      case n => n + " " + plural
    }

  /**
   * Replace v with u, and j with i
   *
   * @param lowerCase A lower-case string
   * @return A string with v -> u, j -> i
   */
  private def replaceVJ(lowerCase: String) =
    lowerCase.
      replaceAllLiterally("v", "u").
      replaceAllLiterally("j", "i")

  /**
   * A regex that matches non-alphabetic characters
   */
  private val nonCharacters = "[0-9]".r

  /**
   * Normalize a key fully
   *
   * @param str A string
   * @return A normalized string
   */
  private def normalize(str: String) = {
    val replaced = replaceVJ(str.toLowerCase)
    nonCharacters.replaceAllIn(replaced, "")
  }

  /**
   * Get a key given a normalization level
   *
   * @param token Any token
   * @param normalizeLevel The user-specified normalization level
   * @return A (possibly-normalized) key
   */
  private def getKey(token: String, normalizeLevel: NormalizeLevel) =
    normalizeLevel match {
      case NoNormalization => token
      case PartialNormalization => replaceVJ(token.toLowerCase)
      case FullNormalization => normalize(token)
    }

  /**
   * The entry point. Get a key, look it up, print any entries found
   *
   * @param args The command line arguments
   */
  def main(args: Array[String]) = {
    val (database, token, normalizeKeys) = parseArgs(args)
    usingDatabase(database) { db =>
      val key = getKey(token, normalizeKeys)
      val key_bytes = key.getBytes("UTF-8")

      logger.info("Key: " + key)

      db.get(key_bytes) match {
        case null =>
          logger.error("No stems found")
          sys.exit(1)
        case valueBytes =>
          val lst: List[CSVLine] = try {
            CSVLine.fromByteArray(valueBytes)
          } catch {
            case e: Exception => {
              logger.warn("Unable to deserialize byte stream", e)
              null
            }
          }

          val list = Option(lst).getOrElse(Nil)
          logger.info("Found " + plural(list.size, "entry", "entries") + ":")
          list.foreach { entry =>
            logger.info("* " + entry.stem)
          }
      }
    }
  }
}
