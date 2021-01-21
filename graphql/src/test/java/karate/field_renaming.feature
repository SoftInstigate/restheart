Feature: Field renaming test

  Background:

    * url graphQLBaseURL
    * path 'mflix'
    * configure charset = null
    * def appDef = read('app-definitionExample.json')
    * call read('upload_app_definition.feature') {appDef: #(appDef)}


  Scenario:
