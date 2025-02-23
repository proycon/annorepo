# AnnoRepoClient

A Java/Kotlin client for connecting to an AnnoRepo server and wrapping the communication with the
endpoints.

## Installation

### Maven

Add the following to your `pom.xml`

```xml
<dependency>
    <groupId>${project.groupId}</groupId>
    <artifactId>${project.artifactId}</artifactId>
    <version>${project.version}</version>
</dependency>
```

## Initialization

**Kotlin:**

```kotlin
val client = AnnoRepoClient(
    serverURI = URI.create("http://localhost:8080"),
    apiKey = apiKey,
    userAgent = "name to identity this client in the User-Agent header"
)
```

`apiKey` and `userAgent` are optional, so in Java there are three constructors:

**Java**

```java
AnnoRepoClient1 client=new AnnoRepoClient(URI.create("http://localhost:8080"));
AnnoRepoClient1 client2=new AnnoRepoClient(URI.create("http://localhost:8080",apiKey));
AnnoRepoClient1 client3=new AnnoRepoClient(URI.create("http://localhost:8080",apiKey,userAgent));
```

The client will try to connect to the AnnoRepo server at the given URI, and throw a RuntimeException if this is not
possible.
After connecting, you will be able to check the version of annorepo that the server is running:

**Kotlin:**

```kotlin
val serverVersion = client.serverVersion
```

**Java**

```java
String serverVersion = client.getServerVersion();
```

as well as whether this server requires authentication:

**Kotlin:**

```kotlin
val serverNeedsAuthentication = client.serverNeedsAuthentication
```

**Java**

```java
Boolean serverNeedsAuthentication = client.getServerNeedsAuthentication();
```

## General information about the endpoint calls

The calls to the annorepo server endpoints will return an
[Either](https://arrow-kt.io/docs/apidocs/arrow-core/arrow.core/-either/) of
a RequestError (in case of an unexpected server response) and
an endpoint-specific result (in case of a successful response).

The `Either` has several methods to handle the left or right hand side;
for example: `fold`, where you provide functions to deal with the left and right sides:

**Kotlin:**

```kotlin
client.getAbout().fold(
    { error -> println(error) },
    { result -> println(result) }
)
```

**Java**

```java
Boolean success=client.getAbout().fold(
    error->{
        System.out.println(error.toString());
        return false;
    },
    result->{
        System.out.println(result.toString());
        return true;
    }
)
```

## Get information about the server

**Kotlin:**

```kotlin
val result = client.getAbout()
```

**Java**

```java
Either<RequestError, GetAboutResult> aboutResult = client.getAbout();
```

## Annotation containers

### Creating a container

Parameters:

- `preferredName`: optional String, indicating the preferred name for the container. May be overridden by the server.
- `label`: optional String, a human-readable label for the container.

**Kotlin:**

```kotlin
val preferredName = "my-container"
val label = "A container for all my annotations"
val success = client.createContainer(preferredName, label).fold(
    { error: RequestError ->
        handleError(error)
        false
    }
) { (_, location, containerName, eTag): CreateContainerResult ->
    doSomethingWith(containerName, location, eTag)
    true
}
```

**Java**

```java
String preferredName = "my-container";
String label = "A container for all my annotations";
Boolean success = client.createContainer(preferredName, label).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        (ARResult.CreateContainerResult result) -> {
            String containerName = result.getContainerName();
            URI location = result.getLocation();
            String eTag = result.getETag();
            doSomethingWith(containerName, location, eTag);
            return true;
        }
);
```

On a succeeding call, the `CreateContainerResult` contains:

- `response` : the raw javax.ws.rs.core.Response
- `location` : the contents of the `location` header
- `containerName` : the name of the container.
- `eTag` : the eTag for the container

### Retrieving a container

**Kotlin:**

```kotlin
val either = client.createContainer()
    .map { (_, _, containerName): CreateContainerResult ->
        client.getContainer(containerName)
            .map { (response, entity, eTag1): GetContainerResult ->
                val entityTag = response.entityTag
                doSomethingWith(eTag1, entity, entityTag)
                true
            }
        true
    }
```

**Java**

```java
String containerName = "my-container"
client.getContainer(containerName).map(
        (ARResult.GetContainerResult result) -> {
            String eTag = result.getETag();
            String entity = result.getEntity();
            EntityTag entityTag = result.getResponse().getEntityTag();
            doSomethingWith(eTag, entity, entityTag);
            return true;
        }
);
```

### Deleting a container

**Kotlin:**

```kotlin
client.deleteContainer(containerName, eTag)
    .map { result: DeleteContainerResult -> true }
```

**Java**

```java
client.deleteContainer(containerName, eTag).map(
        (ARResult.DeleteContainerResult result) -> true
);
```

## Annotations

### Adding an annotation to a container

**Kotlin:**

```kotlin
val containerName = "my-container"
val annotation = WebAnnotation.Builder()
    .withBody("http://example.org/annotation1")
    .withTarget("http://example.org/target")
    .build()
client.createAnnotation(containerName, annotation).fold(
    { error: RequestError ->
        handleError(error)
        false
    }
) { (_, location, _, annotationName, eTag): CreateAnnotationResult ->
    doSomethingWith(annotationName, location, eTag)
    true
}
```

**Java**

```java
String containerName = "my-container";
WebAnnotation annotation = new WebAnnotation.Builder()
        .withBody("http://example.org/annotation1")
        .withTarget("http://example.org/target")
        .build();
client.createAnnotation(containerName, annotation).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        (ARResult.CreateAnnotationResult result) -> {
            URI location = result.getLocation();
            String eTag = result.getETag();
            String annotationName = result.getAnnotationName();
            doSomethingWith(annotationName, location, eTag);
            return true;
        }
);
```

### Retrieving an annotation

**Kotlin:**

```kotlin
val containerName = "my-container"
val annotationName = "my-annotation"
client.getAnnotation(containerName, annotationName).fold(
    { error: RequestError ->
        handleError(error)
        false
    }
) { (_, eTag, annotation): GetAnnotationResult ->
    doSomethingWith(annotation, eTag)
    true
}
```

**Java**

```java
String containerName = "my-container";
String annotationName = "my-annotation";
client.getAnnotation(containerName, annotationName).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        (ARResult.GetAnnotationResult result) -> {
            String eTag = result.getETag();
            Map<String, Object> annotation = result.getAnnotation();
            doSomethingWith(annotation, eTag);
            return true;
        }
);
```

### Updating an annotation

**Kotlin:**

```kotlin
val containerName = "my-container"
val annotationName = "my-annotation"
val eTag = "abcde"
val updatedAnnotation = WebAnnotation.Builder()
    .withBody("http://example.org/annotation2")
    .withTarget("http://example.org/target")
    .build()
client.updateAnnotation(containerName, annotationName, eTag, updatedAnnotation)
    .fold(
        { error: RequestError ->
            handleError(error)
            false
        }
    ) { (_, location, _, _, newETag): CreateAnnotationResult ->
        doSomethingWith(annotationName, location, newETag)
        true
    }
```

**Java**

```java
String containerName = "my-container";
String annotationName = "my-annotation";
String eTag = "abcdefg";
WebAnnotation updatedAnnotation = new WebAnnotation.Builder()
        .withBody("http://example.org/annotation2")
        .withTarget("http://example.org/target")
        .build();
client.updateAnnotation(containerName, annotationName, eTag, updatedAnnotation).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        (ARResult.CreateAnnotationResult result) -> {
            URI location = result.getLocation();
            String newETag = result.getETag();
            doSomethingWith(annotationName, location, newETag);
            return true;
        }
);
```

### Deleting an annotation

**Kotlin:**

```kotlin
val containerName = "my-container"
val annotationName = "my-annotation"
val eTag = "abcdefg"
val success = client.deleteAnnotation(containerName, annotationName, eTag).fold(
    { error: RequestError ->
        handleError(error)
        false
    }
) { _: DeleteAnnotationResult -> true }
```

**Java**

```java
String containerName = "my-container";
String annotationName = "my-annotation";
String eTag = "abcdefg";
Boolean success = client.deleteAnnotation(containerName, annotationName, eTag).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        (ARResult.DeleteAnnotationResult result) -> true
);
```

### Batch uploading of annotations

**Kotlin:**

```kotlin
val containerName = "my-container"
val annotation1 = WebAnnotation.Builder()
    .withBody("http://example.org/annotation1")
    .withTarget("http://example.org/target1")
    .build()
val annotation2 = WebAnnotation.Builder()
    .withBody("http://example.org/annotation2")
    .withTarget("http://example.org/target2")
    .build()
val annotations = java.util.List.of(annotation1, annotation2)
val success = client.batchUpload(containerName, annotations).fold(
    { error: RequestError ->
        handleError(error)
        false
    }
) { (_, annotationIdentifiers): BatchUploadResult ->
    doSomethingWith(annotationIdentifiers)
    true
}
```

**Java**

```java
String containerName = "my-container";
WebAnnotation annotation1 = new WebAnnotation.Builder()
        .withBody("http://example.org/annotation1")
        .withTarget("http://example.org/target1")
        .build();
WebAnnotation annotation2 = new WebAnnotation.Builder()
        .withBody("http://example.org/annotation2")
        .withTarget("http://example.org/target2")
        .build();

List<WebAnnotation> annotations = List.of(annotation1, annotation2);
Boolean success = client.batchUpload(containerName, annotations).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        (ARResult.BatchUploadResult result) -> {
            List<AnnotationIdentifier> annotationIdentifiers = result.getAnnotationData();
            doSomethingWith(annotationIdentifiers);
            return true;
        }
);
```

## Querying a container

### Creating the search

**Kotlin:**

Construct the query as a (nested) map.
When successful, the call returns a createSearchResult, which contains the queryId to be used in getting the result pages.

```kotlin
val query = mapOf("body" to "urn:example:body42")
val createSearchResult = this.createSearch(containerName = containerName, query = query)
```

**Java**

```java
String containerName = "volume-1728";
Map<String, Object> query = Map.of("body.type", "Page");
Boolean success = client.createSearch(containerName, query).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        (ARResult.CreateSearchResult result) -> {
            URI location = result.getLocation();
            String queryId = result.getQueryId();
            doSomethingWith(location, queryId);
            return true;
        }
);
```

### Retrieving a result page

**Kotlin:**

```kotlin
val resultPageResult = this.getSearchResultPage(
    containerName = containerName,
    queryId = queryId,
    page = 0
)
```

**Java**

```java
client.getSearchResultPage(containerName, queryId, 0).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        result -> {
            AnnotationPage annotationPage = result.getAnnotationPage();
            doSomethingWith(annotationPage);
            return true;
        }
);
```

### Filtering Container Annotations

This function combines creating the search and iterating over the search result pages and extracting the annotations from those pages into a stream.
Since there could be unexpected response from the server, the stream returned is one of `Either<RequestError, FilterContainerAnnotationsResult>`


**Kotlin:**

```kotlin
val query = mapOf("body.type" to "Page")
val filterContainerAnnotationsResult: FilterContainerAnnotationsResult? =
    this.filterContainerAnnotations(containerName, query2).orNull()
filterContainerAnnotationsResult?.let {
    it.annotations.forEach { item ->
        item.fold(
            { error: RequestError -> handleError(error) },
            { annotation: Map<String, Any> -> handleSuccess(annotation) }
        )
    }
}
```

**Java**

```java
Map<String, ?> query = Map.of("body.type", "Resolution");
client.filterContainerAnnotations("my-container", query).fold(
        error -> {
            System.out.println(error.toString());
            return false;
        },
        result -> {
            result.getAnnotations().limit(5).forEach(item -> {
                System.out.println(item.orNull());
                System.out.println();
            });
            return true;
        }
)
```

### Retrieving search information

**Kotlin:**

```kotlin
val getSearchInfoResult = this.getSearchInfo(
    containerName = containerName,
    queryId = queryId
)
```

**Java**

```java
Boolean success = client.getSearchInfo(containerName, queryId).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        result -> {
            SearchInfo searchInfo = result.getSearchInfo();
            doSomethingWith(searchInfo);
            return true;
        }
);

```

## Indexes

### Adding an index to a container

**Kotlin:**

```kotlin
val containerName = "volume-1728"
val fieldName = "body.type"
val indexType = IndexType.HASHED
val success = client.addIndex(containerName, fieldName, indexType).fold(
    { error: RequestError ->
        handleError(error)
        false
    },
    { result: AddIndexResult -> true }
)
```

**Java**

```java
String containerName = "volume-1728";
String fieldName = "body.type";
IndexType indexType = IndexType.HASHED;
Boolean success = client.addIndex(containerName, fieldName, indexType).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        result -> true
);
```

### Retrieving index information

**Kotlin:**

```kotlin
val containerName = "volume-1728"
val fieldName = "body.type"
val indexType = IndexType.HASHED
val success = client.getIndex(containerName, fieldName, indexType).fold(
    { error: RequestError ->
        handleError(error)
        false
    }, { (_, indexConfig): GetIndexResult ->
        doSomethingWith(indexConfig)
        true
    }
)
```

**Java**

```java
String containerName = "volume-1728"; 
String fieldName = "body.type";
IndexType indexType = IndexType.HASHED;
Boolean success = client.getIndex(containerName, fieldName, indexType).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        (ARResult.GetIndexResult result) -> {
            IndexConfig indexConfig = result.getIndexConfig();
            doSomethingWith(indexConfig);
            return true;
        }
);
```

### Listing all indexes for a container

**Kotlin:**

```kotlin
val containerName = "volume-1728"
val success = client.listIndexes(containerName).fold(
    { error: RequestError ->
        handleError(error)
        false
    }, { (_, indexes): ListIndexesResult ->
        doSomethingWith(indexes)
        true
    }
)
```

**Java**

```java
String containerName = "volume-1728";
Boolean success = client.listIndexes(containerName).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        (ARResult.ListIndexesResult result) -> {
            List<IndexConfig> indexes = result.getIndexes();
            doSomethingWith(indexes);
            return true;
        }
);
```

### Deleting an index

**Kotlin:**

```kotlin
val containerName = "volume-1728"
val fieldName = "body.type"
val indexType = IndexType.HASHED
val success = client.deleteIndex(containerName, fieldName, indexType).fold(
    { error: RequestError ->
        handleError(error)
        false
    },
    { result: DeleteIndexResult -> true }
)
```

**Java**

```java
String containerName = "volume-1728";
String fieldName = "body.type";
IndexType indexType = IndexType.HASHED;
Boolean success = client.deleteIndex(containerName, fieldName, indexType).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        (ARResult.DeleteIndexResult result) -> true
);

```

## Retrieving information about the fields used in container annotations

**Kotlin:**

```kotlin
val containerName = "volume-1728"
client.getFieldInfo(containerName).fold(
    { error: RequestError -> handleError(error) },
    { (_, fieldInfo): AnnotationFieldInfoResult -> doSomethingWith(fieldInfo) }
)
```

**Java**

```java
String containerName = "volume-1728";
client.getFieldInfo(containerName).fold(
        (RequestError error) -> {
            handleError(error);
            return false;
        },
        result -> {
            Map<String, Integer> fieldInfo = result.getFieldInfo();
            doSomethingWith(fieldInfo);
            return true;
        }
);
```

## User administration

These admin functionalities are only available on annorepo servers that have authentication enabled.
The root api-key is required for these calls.

### Adding users

**Kotlin:**

```kotlin
val userEntries = listOf(UserEntry(userName, apiKey))
client.addUsers(userEntries).fold(
    { error -> println(error.message) },
    { result -> 
        val accepted = result.accepted
        val rejected = result.rejected
        doSomething(accepted, rejected)
    }
)
```

**Java**

```java
List<UserEntry> userEntries = List.of(new UserEntry("userName", "apiKey"));
client.addUsers(userEntrirs).fold(
        error -> 
            System.out.println(error.getMessage());
            return false;
        },
        result -> {
            List<String> accepted = result.getAccepted();
            List<RejectedUserEntry> rejected = result.getRejected();
            doSomething(accepted,rejected);
            return true;
        }
);
```

### Retrieving users

**Kotlin:**

```kotlin
client.getUsers().fold(
    { error: RequestError -> println(error) },
    { result: ARResult.UsersResult ->
        result.userEntries.forEach { ue: UserEntry ->
            val userName: String = ue.userName
            val apiKey: String = ue.apiKey
            doSomething(userName, apiKey)
        }
    }
)
```

**Java**

```java
client.getUsers().fold(
        error -> {
            System.out.println(error.getMessage());
            return false;
        },
        result -> {
            List<UserEntry> userEntries = result.getUserEntries();
            for (UserEntry ue : userEntries) {
                String userName = ue.getUserName();
                String apiKey = ue.getApiKey();
                doSomething(userName, apiKey);
            }
            return true;
        }
);

```

### Deleting a user

**Kotlin:**

```kotlin
val deletionSucceeded = client.deleteUser(userName).isRight()
```

**Java**

```java
Boolean deletionSucceeded = client.deleteUser(userName).isRight();
```
