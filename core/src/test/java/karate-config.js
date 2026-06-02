function fn() {
  var env = karate.env; // get system property 'karate.env'
  karate.log('karate.env system property was:', env);
  if (!env) {
    env = 'dev';
  }
  var config = {
    env: env,
    myVarName: 'someValue',
    rhBasicAuth: 'Basic YWRtaW46c2VjcmV0',
    graphQLBaseURL: 'http://localhost:8080/graphql',
    restheartBaseURL: 'http://localhost:8080',
    contTypeGraphQL: 'application/graphql',
    contTypeJson: 'application/json',
    // accounts tests aliases
    baseUrl: 'http://localhost:8080',
    adminAuth: 'Basic YWRtaW46c2VjcmV0',
    ownerEmail: 'owner-test@example.com',
    ownerPass: 'OwnerPass123!',
    ownerAuth: 'Basic b3duZXItdGVzdEBleGFtcGxlLmNvbTpPd25lclBhc3MxMjMh',
    jwtSecret: 'secret'
  };

  karate.configure('headers', { 'X-Skip-Email': 'true' });

  if (env === 'dev') {
    // customize
    // e.g. config.foo = 'bar';
  } else if (env === 'e2e') {
    // customize
  }
  return config;
}