# RESTHeart Platform Security

## Plan

### Version 1.0

#### RESTHeart Authenticator

An Authenticator that uses RESTHeart as users store.

- port DbIdentitiManager to use unirest to get data - DONE!
- filters out the password property from responses - DONE!
- encrypts passwords on write requests  - DONE!
- forbid requests with filter qparam involving password property - DONE!
- change license to Apache 2.0
- add LicenseVerifier (RH will work + specific uIAM license)

- a RH transformer that injects the AuthenticatedUser from X_FORWARD headers.

#### JWT authentication mechanism

- authentication mechanism based on JSON Web Token specification - DONE!
- allow custom verification of the token - DONE!
- consider moving rh idm configuration to uiam.yml from security.yml - DONE!
- allow to pass the token to the backend - DONE!

### Version 2.0

#### RESTHeart AM

An AM that uses RESTHeart as permissions store.

#### UX

A UX to manage users and permissions, inspired by mrest.io dashboard.

#### Gherkin as Permission Language?

Define a Permission Language that allows checking the requests including the body. Inspired by Gherkin https://docs.cucumber.io/gherkin/reference/