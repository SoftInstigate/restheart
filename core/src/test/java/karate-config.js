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
    contTypeJson: 'application/json'
  };
  
  if (env === 'dev') {
    // customize
    // e.g. config.foo = 'bar';
  } else if (env === 'e2e') {
    // customize
  }
  return config;
}