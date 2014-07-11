package edu.virginia.lib.bibframerator

import java.io.File
import java.nio.file.{ Path, Paths, SimpleFileVisitor, WatchEvent, WatchKey, WatchService }
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.Files.walkFileTree
import java.nio.file.StandardWatchEventKinds.{ ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY }
import java.nio.file.attribute.BasicFileAttributes
import Console.err
import collection.JavaConversions.asScalaBuffer
import collection.mutable.Map
import concurrent.ExecutionContext.Implicits.global
import concurrent.future
import language.{ implicitConversions, postfixOps }
import sys.exit
import util.control.Breaks.{ break, breakable }
import javax.xml.transform.stream.StreamSource
import net.sf.saxon.s9api.Processor
import net.sf.saxon.s9api.Serializer.Property.{ INDENT, METHOD }
import net.sf.saxon.s9api.XQueryExecutable

object Bibframerator extends Runnable {

  val libraryUri = "http://www.lib.virginia.edu/"

  val event_types = Array(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)

  var watchedDir: Path = null
  var transformedDir: Path = null

  var watcher: WatchService = null

  val keys: Map[WatchKey, Path] = Map()

  val xqueryProcessor = new Processor(false)

  val classloader = this.getClass getClassLoader

  val compiler = xqueryProcessor newXQueryCompiler ()

  var xquery: XQueryExecutable = null

  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      err println ("Usage: bibframerator /from/dir transform-file /to/dir")
      exit(1)
    }
    watchedDir = args(0)
    xquery = compiler compile (new File(args(1)))
    transformedDir = args(2)
    new Thread(this) start
  }

  def run: Unit = {

    try {
      watcher = watchedDir.getFileSystem newWatchService

      keys put (watchedDir register (watcher, event_types: _*), watchedDir)

      walkFileTree(watchedDir, new SimpleFileVisitor[Path]() {
        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
          keys put (dir register (watcher, event_types: _*), dir)
          println(s"Registered $dir for observation")
          CONTINUE
        }
      })

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

          def actOnEvent(event: WatchEvent[_]) = {

            val filename = event.context.asInstanceOf[Path]
            val sourceDir = keys(watchKey)
            val sourcePath = sourceDir resolve (filename)
            println(s"Received event from path: $sourcePath")
            val destDir = transformedDir resolve (sourceDir.subpath(watchedDir getNameCount, sourceDir getNameCount))
            val transformedPath = destDir resolve (filename)

            event kind match {
              case ENTRY_CREATE => {
                if (sourcePath isDirectory) {
                  sourcePath register (watcher, event_types: _*)
                  println(s"Registered $sourcePath for observation")
                  transformedPath mkdir
                } else {
                  doTransform
                }
              }
              case ENTRY_MODIFY => {
                if (sourcePath isFile)
                  doTransform
              }
              case ENTRY_DELETE => {
                println(s"Deleting $transformedPath")
                if (!(transformedPath delete)) {
                  err println (s"Failed to delete $transformedPath")
                }
              }
            }

            def doTransform {
              val transform = xquery load;
              transform setSource (new StreamSource(sourcePath))
              val output = xqueryProcessor newSerializer (transformedPath)
              output setOutputProperty (METHOD, "xml")
              output setOutputProperty (INDENT, "yes")
              try {
                transform setDestination (output)
                transform run
              } finally output close;
              println(s"Wrote to $transformedPath")
            }
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

  implicit def s2p(s: String): Path = Paths get s

  implicit def p2f(p: Path): File = p toFile

}