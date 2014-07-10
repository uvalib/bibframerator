A simple utility for use as part of UVa's experiments with the [Bibframe](http://bibframe.org/) project.

The major dependency is the well-know [Saxon](http://www.saxonica.com/) XSLT/XQuery engine.

Build it with Scala's SBT utility via: `sbt assembly`. The resulting executable artifact will be found in `target/scala-2.10/bibframerator`.

Run it via `bibframerator /directory/to/watch xquery-transformation-file /directory/into/which/to/put/results`. Options for the Java VM can be supplied in environment variable `JAVA_TOOL_OPTIONS`.

As long as the utility is operating, it will watch the first directory and any subdirectories for any changes, via the operating system's filesystem notification service (e.g. `inotify` on Linux or Windows' Directory Change Notifications). When it hears about a new or changed file, it will transform it via the supplied XQuery transformation and put the results in the second directory, respecting hierarchy. If a file is removed from the first directory, its corresponding file will be removed from the second.

The expectation is that files in the watched directory will be well-formed XML. We intend to use this utility to "practice" making Bibframe RDF from MARC XML.
