# RESTHeart Platform Core

## Ho to pass the license key

The default location of the license key file is the directory *lickey* next to the file restheart-platform-core.jar

- restheart-platform-core.jar
- lickey
    - comm-license.key
    - COMM-LICENSE.txt

The lickey directory path can be specified with the lk-dir java property:

```
$ java -Dlk-dir=<dir> -jar restheart-platform-core.jar
```

## Accept the license via property

In order to self-accept the license agreement, run it as follows:

```
$ java -DlACCEPT_LICENSE_AGREEMENT=true -jar restheart-platform-core.jar
```