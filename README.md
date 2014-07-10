A simple utility for use as part of UVa's experiments with the [Bibframe](http://bibframe.org/) project.

Build it with Scala's SBT utility via: `sbt assembly`. The resulting executable artifact will be found in `target/scala-2.10/bibframerator`.

Run it via `bibframerator /directory/to/watch xquery-transformation-file /directory/into/which/to/put/results`.

As long as the utility is operating, it will watch the first directory for any changes. When it finds a new or changed file, it will transform it via the supplied XQuery transformation and put the results in the second directory, respecting hierarchy. If a file is removed from the first directory, its corresponding file will be removed from the second.

