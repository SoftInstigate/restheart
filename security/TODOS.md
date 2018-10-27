# refactoring

- authentication mechanism is added via Factory pattern. turn it to intefact PluggableAuthenticationManager
- need to allow plugging several authentication mechanism
- BasicAuthenticationMechanism is always added. It need to be plugged via configuration as others mechanism.
- AuthTokenAuthenticationMechanism is controlled via auth-token-enabled option. It need to be plugged via configuration as others mechanism.
- RequestContext class is useless. Implement util class to simplify usage of HttpServerExchange.

# process automation

- build on push (github actions or travis-ci)
- publish to maven central
- build docker image

# itegration tests

- create integration tests (reusing restheart integration tests)

# javadoc

- check javadoc including package-info