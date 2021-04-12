# DE4A - CONNECTOR
Connector component. Check out following instructions and descriptions.

## de4a-commons
Concived as a library that maintains:
- Utils and general purpose methods
- Common classes

## de4a-idk
IDK entity mock based on [API definition](https://app.swaggerhub.com/apis/danieldecastrop/swagger-idk_de_4_a_information_desk/2.0.1#/). Maintains provided information on in-memory DB tables. Mock is able to provide repsonse to the interfaces:
- /idk/ial/{canonicalEvidenceTypeId}
- /idk/ial/{canonicalEvidenceTypeId}/{countryCode}
- /idk/provision

### Configuration
#### H2 In-Memory database
Inserts sql file to set up information provided by service
```sh
idk/src/main/resources/import.sql
```
#### Properties
```sh
idk/src/main/resources/application.properties
```
#### Spring config

```sh
idk/src/main/eu/idk/configuration/Conf.java
```

## de4a-connector
Checkout technical documentation [DE4A Connector - Installation and configuration v1.0.docx] (`pending link to owncloud`)
### API doc
Once you deploy a Connector instace it be able to access to Swagger UI browsing:
```sh
http://endpoint:port/swagger-ui/
```
Even so, API definition is published on:
[Public Swagger API Connector](https://app.swaggerhub.com/apis/de4a/Connector/0.1.0)

[![Architecture diagram](data:image/jpeg;base64,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)](https://prezi.com/view/gqp9mPI7eBxstHwkVoWD/)

## INSTALL
You should be able to compile hole packages from parent POM file:
```sh
mvn clean install -Dmaven.test.skip=true
```
Also it is possible to compile each package separately browsing into the folder and running the command above

**Until [de4a-commons](https://github.com/de4a-wp5/de4a-commons) is on maven central) install the de4a-commons project locally.**

#### de4a-commons
Project includes [de4a-commons](https://github.com/de4a-wp5/de4a-commons/tree/development) as a library in order to use utils and JAXB objects generated from schemes. Due to latest schemes updates, de4a-commons is on snapshot version located at developtment branch of repository, so, it is necessary to install it locally until releases versions are on public repositories.

#### Toop version
Due last changes on [de4a-commons](https://github.com/de4a-wp5/de4a-commons/tree/development) Toop-connector-ng version should be 2.1.2-SNAPSHOT, which it is possible you need to add following repo server on your maven settings
```sh
https://oss.sonatype.org/content/repositories/snapshots/
```
