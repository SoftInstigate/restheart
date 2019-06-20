# RESTHeart Platform packager

Clone this repository, then cd into it and clone (or copy) the restheart-platform-core and restheart-platform-security projects.

```
git clone git@bitbucket.org:softinstigate/restheart-platform-core.git
git clone git@bitbucket.org:softinstigate/restheart-platform-security.git
```

To build and package a new release of the RESTHeart Platform:

```
$ ./package.sh <version>
```

You must provide a "version". This builds both core and security projects with Maven, creates a folder `restheart-platform-<version>/` and a zip file `restheart-platform-<version>.zip`. 

Finally, upload the zip file somewhere to make it available.
