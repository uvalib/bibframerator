package edu.virginia.lib.bibframerator

import java.io.File
import java.nio.file.{ Path, Paths }
import java.nio.file.StandardWatchEventKinds.{ ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY }
import java.nio.file.WatchEvent

import Console.err
import collection.JavaConversions.asScalaBuffer
import concurrent.ExecutionContext.Implicits.global
import concurrent.future
import language.{ implicitConversions, postfixOps }
import sys.exit
import util.control.Breaks.{ break, breakable }

import javax.xml.transform.stream.StreamSource
import net.sf.saxon.s9api.{ Processor, QName }
import net.sf.saxon.s9api.{ XQueryExecutable, XdmAtomicValue }
import net.sf.saxon.s9api.Serializer.Property.{ INDENT, METHOD }

object Bibframerator extends Runnable {

  val libraryUri = "http://www.lib.virginia.edu/"

  var watchedDir: Path = null
  var transformedDir: Path = null

  val xqueryProcessor = new Processor(false)

  val classloader = this.getClass getClassLoader

  val compiler = xqueryProcessor newXQueryCompiler ()

  var xquery: XQueryExecutable = null

  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      err println ("Usage: bibframerator /from/dir transform /to/dir")
      exit(1)
    }

    watchedDir = Paths get (args(0))
    xquery = compiler compile (new File(args(1)))
    transformedDir = Paths get (args(2))
    new Thread(this) start
  }

  def run: Unit = {

    try {
      val watcher = watchedDir.getFileSystem newWatchService

      watchedDir register (watcher, ENTRY_CREATE,
        ENTRY_DELETE, ENTRY_MODIFY);

      breakable {
        while (true) {
          // blocks on next file event
          val watchKey = watcher take

          try watchKey.pollEvents foreach {
            event =>
              future { actOnEvent(event) } onFailure {
                case e: Throwable => {
                  err println (s"Failed to operate on $event with exception $e")
                  e printStackTrace (err)
                }
              }
          }
          catch {
            case e: Exception => {
              err println ("Exception: " + e)
              e printStackTrace (err)
            }
          }

          if (!(watchKey reset)) {
            err println ("Watchkey no longer valid!")
            watchKey cancel ()
            watcher close ()
            break
          }
        }
      }
    } catch {
      case ie: InterruptedException => exit(0)
      case e: Exception => {
        err println ("Exception: " + e)
        e printStackTrace (err)
        exit(1)
      }
    }
  }

  def actOnEvent(event: WatchEvent[_]) = {

    val localfilename = event.context.asInstanceOf[Path]
    val sourcePath = watchedDir resolve (localfilename)
    println(s"Received event from path: $sourcePath")
    val transformedPath = transformedDir resolve (localfilename)

    event kind match {
      case ENTRY_CREATE | ENTRY_MODIFY => {

        val transform = xquery load ()
        transform setExternalVariable (new QName("baseuri"), new XdmAtomicValue(libraryUri))
        transform setExternalVariable (new QName("serialization"), new XdmAtomicValue("RDFXMLF"))

        transform setSource (new StreamSource(sourcePath toFile))
        val output = xqueryProcessor newSerializer (transformedPath toFile)
        output setOutputProperty (METHOD, "xml")
        output setOutputProperty (INDENT, "yes")
        try {
          transform setDestination (output)
          transform run
        } finally output close
        
        println(s"Wrote to $transformedPath")
      }
      case ENTRY_DELETE => {
        println(s"Deleting $transformedPath")
        if (!(transformedPath.toFile delete)) {
          err println (s"Failed to delete $transformedPath")
        }
      }
    }
  }

}