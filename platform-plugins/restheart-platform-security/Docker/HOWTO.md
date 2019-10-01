# &#181;IAM Docker HOWTO

## build the docker image

```shell
$ ./Docker/build.sh
```

## run container 

```shell
$ docker run -i -t --rm --name restheart-platform-security -p 8080:8080 -p 4443:4443 softinstigate/restheart-platform-security
```

CTRL+D to stop and remove the container