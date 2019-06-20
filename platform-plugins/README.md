# RESTHeart Platform packager

Clone this repository, then cd into it and clone (or copy) the `restheart-platform-core`,  `restheart-platform-security` and `si-lka` projects.

```
git clone git@bitbucket.org:softinstigate/restheart-platform-core.git
git clone git@bitbucket.org:softinstigate/restheart-platform-security.git
git clone git@bitbucket.org:softinstigate/si-lka.git
```

To build and package a new release of the RESTHeart Platform:

```
$ ./package.sh <version>
```

The "version" parameter is mandatory. This builds both core and security projects with Maven, creates a folder `restheart-platform-<version>/` and a zip file `restheart-platform-<version>.zip`. 

Finally, upload the zip file somewhere to make it available.
