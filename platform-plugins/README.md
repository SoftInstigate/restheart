# RESTHeart Platform packager

This projects packages the RESTHeart Platform files into a single zip file to make it available to the public.

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

The "version" parameter is mandatory. This performs the following steps:

1. Cleans-up previous folders and zip files
2. builds both core and security projects with Maven;
3. creates a folder named `restheart-platform-<version>/`;
4. moves the `template/` folder's content into `restheart-platform-<version>/`;
5. compress the `restheart-platform-<version>/` folder into a zip file named `restheart-platform-<version>.zip`. 

The structure of the distributable zip file will be like this:

```
.
├── Docker/
│   ├── Dockerfile-core
│   ├── Dockerfile-security
│   └── etc/
│       ├── acl.yml
│       ├── core.properties
│       ├── restheart-platform-core.yml
│       ├── restheart-platform-security.yml
│       └── users.yml
├── core.log
├── docker-compose.yml
├── etc/
│   ├── bwcv3.properties
│   ├── default.properties
│   ├── restheart-platform-core.yml
│   ├── restheart-platform-security.yml
│   └── standalone.properties
├── lickey/
│   └── COMM-LICENSE.txt
├── restheart-platform-core.jar
├── restheart-platform-security.jar
└── security.log

```

Finally, upload the zip file somewhere to make it available.
