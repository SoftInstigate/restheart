## RESTHeart

You find the full documentation at [http://www.restheart.org](http://www.restheart.org)

### RESTHeart is the REST API server for mongodb

* Zero development time: just start it and the data REST API is ready to use
* CRUD operations API on your data
* Data model operations API: create dbs, collections, indexes and the data structure
* Super easy setup with convention over configuration approach
* Pluggable security with User Management and ACL
* HAL hypermedia type
* Super lightweight: pipeline architecture, ~6Mb footprint, ~200Mb RAM peek usage, starts in milliseconds,..
* High throughput: very small overhead on mongodb performance
* Horizontally scalable: fully stateless architecture supporting mongodb replica sets and shards
* Built on top of undertow non-blocking web server
* Embeds the excellent HAL browser by Mike Kelly (the author of the HAL specifications)
* Support Cross-origin resource sharing (CORS) so that your one page web application can deal with RESTHeart running on a different domain. In other words, CORS is an evolution of JSONP
* Ideal as AngularJS (or any other MVW javascript framework) back-end

### How to run it

> RESTHeart requires java 1.8, make sure you have it and available on your path.

Download the latest release from [github releases page](https://github.com/SoftInstigate/restheart/releases), unpack the archive and just run the jar.

	$ java -server -jar restheart.jar
	
You might also want to specify a configuration file:

	$ java -server -jar restheart.jar etc/restheart.yml
	
configuration file [documentation](http://www.restheart.org/docs/v0.9/#/configuration)
example configuration file [restheart.yml](http://www.restheart.org/docs/v0.9/#/configuration/example)
	
### How to build it

Clone the repository and update the git submodules (the HAL browser is included in restheart as a submodule):  

    $ git submodule update --init --recursive 
    
Build the project with maven

    $ mvn clean package
    
Optionally run the integration test suite (make sure mongod is running on localhost on default port 27017 without authentication, i.e. no --auth option specified)

    $ mvn integration-test
   
