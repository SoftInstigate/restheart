# **restheart-security** Docker HOWTO

## build the docker image

```shell
$ ./Docker/build.sh
```

## run container 

```shell
$ docker run -i -t --rm --name restheart-security -p 8080:8080 -p 4443:4443 softinstigate/restheart-security
```

CTRL+D to stop and remove the container