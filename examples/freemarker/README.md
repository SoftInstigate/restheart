# Server-side HTML generation with Apache Freemarker

This example uses [Apache Freemarker](https://freemarker.apache.org/) to generate server-side HTML.

_Apache FreeMarker™ is a template engine: a Java library to generate text output (HTML web pages, e-mails, configuration files, source code, etc.) based on templates and changing data. Templates are written in the FreeMarker Template Language (FTL), which is a simple, specialized language (not a full-blown programming language like PHP). Usually, a general-purpose programming language (like Java) is used to prepare the data (issue database queries, do business calculations). Then, Apache FreeMarker displays that prepared data using templates. In the template you are focusing on how to present the data, and outside the template you are focusing on what data to present._

## Setup

Build the JAR and copy it in RESTHeart's `plugins/` folder as usual, then point your browser to the local running instance at <http://localhost:8080/site>

You can add a `user` query parameter to see it rendered server-side (e.g. <http://localhost:8080/site?user=Anna>).

See the main [README](../README.md) for general setup instructions.
