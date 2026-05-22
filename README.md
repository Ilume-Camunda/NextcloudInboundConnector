# Nextcloud Inbound Connector

The Nextcloud Inbound Connector for Camunda 8 is a custom connector that enables you to respond to external requests from Nextcloud and pass the corresponding metadata directly to Camunda 8 BPMN processes. 

Using Nextcloud’s Webhook app, you can respond to actions such as file and folder operations, send POST requests, and use the connector to pass the relevant data to existing process instances.

This allows for the use of a simple control instance that determines which requests are permitted. Additionally, you can specify how information regarding document-based workflows in Nextcloud should be displayed and processed, whether for archiving, document routing, reporting, or file synchronization. The connector is suitable for both SaaS and self-managed environments and supports modern security and deployment standards.

Currently, the connector is designed to respond to Nextcloud requests sent when managing files within specific folders.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Nextcloud](#nextcloud)
- [Connector Structure](#connector-structure)
- [Build](#build)
- [Running the Connector](#element-template)

## Prerequisites
- A working Nextcloud environment.
- The Webhook App from the Nextcloud Marketplace.
- A local (self-managed) Camunda installation or setup of Camunda SaaS.
- A BPMN diagram detailing the process that uses this connector.

Further information about Connectors and how to set up Camunda 8 can be found through the following links:

Camunda 8 Setup: https://developers.camunda.com/install-camunda-8/  
Connector Setup: https://docs.camunda.io/docs/self-managed/components/connectors/connectors-configuration/

The Nextcloud Webhook App can be found here: https://apps.nextcloud.com/apps/webhooks

## Nextcloud
Before you can start using your connector, you need to set up the Webhook app within Nextcloud. This allows you to send POST requests to a specific endpoint—in this case, the one the connector is listening on. To do this, you need to create a new webhook by clicking on your profile picture in your Nextcloud environment, selecting “Administration Settings,” choosing ‘Workflows’ under “Administration,” and then clicking the “Add New Workflow” button. 
Once this is done, a new entry will open where you can configure the webhook settings. Using configurable filters, you can now define the rules that trigger the webhook to send a request with the metadata after a certain action. This could be, for example, the upload of a file to a specific folder. Additionally, you must specify the endpoint to which the request should be sent (e.g., http://{ip-address}:{port}/{http-path}). 

Once this is configured, Nextcloud can respond to changes within the environment.

## Connector Structure
The connector template contains two major areas that are essential for its functionality:

- Properties
- Correlation
- Whitelist

### Properties
For the connector to be able to listen to a specific endpoint you have to set the port the connector listens on for Nextcloud Events and the HTTP-path to receive events. The value of these settings should be equivalent to the ones you used for the URL in your Nextcloud Webhook.

### Correlation
The correlation settings ensure that the POST requests sent by Nextcloud do not trigger every single process instance currently running in your Camunda environment. This is achieved using two correlation keys, which are compared when the connector receives a request from Nextcloud. To do this, both keys must be created: one within the Camunda process and one within Nextcloud. For example, using Camunda’s native “uuid()” method in a task allows you to automatically create a key as a process variable. To do this in Nextcloud, it is advisable to create a folder whose name consists of the key. This can be done either manually, through custom created logic on your end or by using the Ilume Nextcloud Outbound Connector, which can generate a new folder with the UUID as its name.

### Whitelist
The connector uses a whitelist that can be configured in the project's “application.properties” file. It contains keywords that must be included in the path of the uploaded file in Nextcloud. If none of the keywords are present, the request is rejected.

## Build

You can package the Connector by running the following command:

```bash
mvn clean package
```

This will create the following artifacts:

- A thin JAR without dependencies.
- A fat JAR containing all dependencies, potentially shaded to avoid classpath conflicts. This will not include the SDK
  artifacts since those are in scope `provided` and will be brought along by the respective Connector Runtime executing
  the Connector.

## Element Template

The element template is generated automatically based on the connector
input class using
the [Element Template Generator](https://github.com/camunda/connectors/tree/main/element-template-generator/core).

The generation is embedded in the Maven build and can be triggered by running `mvn clean package`.

The generated element template can be found
in [element-templates/template-connector.json](./element-templates/template-connector.json).
