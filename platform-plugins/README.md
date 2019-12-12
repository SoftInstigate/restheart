# RESTHeart Platform packager

This projects packages the RESTHeart Platform files into a single zip file to make it available to the public.

## Automatic build and deployment

Tag the release with a version number and the Bitbucket pipeline will build, package and upload to the S3 bucket at `download.restheart.com`.

## Manual build and deployment

Clone this repository, then cd into it and clone (or copy) the `restheart-platform-core`,  `restheart-platform-security` and `si-lka` projects.

To [build](build.sh) and [package](package.sh) a new release of the RESTHeart Platform:

```
$ ./build.sh
$ ./package.sh <version>
```

The "version" parameter is mandatory. This performs the following steps:

1. Cleans-up previous folders and zip files
1. Creates the `dist/` folder
1. builds both core and security projects with Maven;
1. creates a folder named `restheart-platform-<version>/` into `dist/`;
1. moves the `template/` folder's content into `dist/restheart-platform-<version>/`;
1. compress the `dist/restheart-platform-<version>/` folder into a zip file named `dist/restheart-platform-<version>.zip`.

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

Finally, upload the zip file to publish it:

```bash
$ ./upload.sh
```

The [upload.sh](upload.sh) script copies any file with name pattern `dist/restheart-platform-*.zip` to the s3 bucket named `download.restheart.com`.

## Set new version

The `setversion.sh` script can be used to update the parent POM and all modules referencing it. For example:

    $ ./setversion.sh 4.2.0-SNAPSHOT
