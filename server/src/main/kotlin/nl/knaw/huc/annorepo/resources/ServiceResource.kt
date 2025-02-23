package nl.knaw.huc.annorepo.resources

import com.codahale.metrics.annotation.Timed
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Aggregates.limit
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import nl.knaw.huc.annorepo.api.ANNO_JSONLD_URL
import nl.knaw.huc.annorepo.api.ARConst
import nl.knaw.huc.annorepo.api.ARConst.ANNOTATION_FIELD
import nl.knaw.huc.annorepo.api.ARConst.ANNOTATION_NAME_FIELD
import nl.knaw.huc.annorepo.api.ARConst.CONTAINER_NAME_FIELD
import nl.knaw.huc.annorepo.api.ARConst.SECURITY_SCHEME_NAME
import nl.knaw.huc.annorepo.api.AnnotationPage
import nl.knaw.huc.annorepo.api.ContainerMetadata
import nl.knaw.huc.annorepo.api.IndexConfig
import nl.knaw.huc.annorepo.api.IndexType
import nl.knaw.huc.annorepo.api.ResourcePaths.FIELDS
import nl.knaw.huc.annorepo.api.ResourcePaths.SERVICES
import nl.knaw.huc.annorepo.api.SearchInfo
import nl.knaw.huc.annorepo.config.AnnoRepoConfiguration
import nl.knaw.huc.annorepo.resources.tools.AggregateStageGenerator
import nl.knaw.huc.annorepo.resources.tools.AnnotationList
import nl.knaw.huc.annorepo.resources.tools.QueryCacheItem
import nl.knaw.huc.annorepo.service.UriFactory
import org.bson.Document
import org.eclipse.jetty.util.ajax.JSON
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit
import javax.annotation.security.PermitAll
import javax.ws.rs.BadRequestException
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.NotFoundException
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.SecurityContext
import javax.ws.rs.core.UriBuilder

@Path(SERVICES)
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
@SecurityRequirement(name = SECURITY_SCHEME_NAME)
class ServiceResource(
    private val configuration: AnnoRepoConfiguration, client: MongoClient,
) {
    private val uriFactory = UriFactory(configuration)
    private val mdb = client.getDatabase(configuration.databaseName)

    private val paginationStage = limit(configuration.pageSize)
    private val aggregateStageGenerator = AggregateStageGenerator(configuration)

    private val queryCache: Cache<String, QueryCacheItem> =
        Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.HOURS).maximumSize(1000).build()
    private val log = LoggerFactory.getLogger(javaClass)

    @Operation(description = "Find annotations in the given container matching the given query")
    @Timed
    @POST
    @Path("{containerName}/search")
    fun createSearch(
        @PathParam("containerName") containerName: String,
        queryJson: String,
        @Context context: SecurityContext,
    ): Response {
        checkContainerExists(containerName)
        val queryMap = JSON.parse(queryJson)
        if (queryMap is HashMap<*, *>) {
            val aggregateStages =
                queryMap.toMap().map { (k, v) -> aggregateStageGenerator.generateStage(k, v) }.toList()
            val container = mdb.getCollection(containerName)
            val count = container.aggregate(aggregateStages).count()

            val id = UUID.randomUUID().toString()
            queryCache.put(id, QueryCacheItem(queryMap, aggregateStages, count))
            val location = uriFactory.searchURL(containerName, id)
            return Response.created(location).link(uriFactory.searchInfoURL(containerName, id), "info")
                .entity(mapOf("hits" to count)).build()
        }
        return Response.status(Response.Status.BAD_REQUEST).build()
    }

    @Operation(description = "Get the given search result page")
    @Timed
    @GET
    @Path("{containerName}/search/{searchId}")
    fun getSearchResultPage(
        @PathParam("containerName") containerName: String,
        @PathParam("searchId") searchId: String,
        @QueryParam("page") page: Int = 0,
        @Context context: SecurityContext,
    ): Response {
        checkContainerExists(containerName)

        val queryCacheItem = getQueryCacheItem(searchId)
        val aggregateStages = queryCacheItem.aggregateStages.toMutableList().apply {
            add(Aggregates.skip(page * configuration.pageSize))
            add(paginationStage)
        }
//        log.debug("aggregateStages=\n  {}", Joiner.on("\n  ").join(aggregateStages))

        val annotations =
            mdb.getCollection(containerName).aggregate(aggregateStages).map { a -> toAnnotationMap(a, containerName) }
                .toList()
        val annotationPage =
            buildAnnotationPage(uriFactory.searchURL(containerName, searchId), annotations, page, queryCacheItem.count)
        return Response.ok(annotationPage).build()
    }

    @Operation(description = "Get information about the given search")
    @Timed
    @GET
    @Path("{containerName}/search/{searchId}/info")
    fun getSearchInfo(
        @PathParam("containerName") containerName: String,
        @PathParam("searchId") searchId: String,
        @Context context: SecurityContext,
    ): Response {
        checkContainerExists(containerName)
        val queryCacheItem = getQueryCacheItem(searchId)
//        val info = mapOf("query" to queryCacheItem.queryMap, "hits" to queryCacheItem.count)
        val query = queryCacheItem.queryMap as Map<String, Any>
        val searchInfo = SearchInfo(
            query = query,
            hits = queryCacheItem.count
        )
        return Response.ok(searchInfo).build()
    }

    @Operation(description = "Get a list of the fields used in the annotations in a container")
    @Timed
    @GET
    @Path("{containerName}/$FIELDS")
    fun getAnnotationFieldsForContainer(
        @PathParam("containerName") containerName: String,
    ): Response {
        checkContainerExists(containerName)
        val containerMetadataCollection = mdb.getCollection<ContainerMetadata>(ARConst.CONTAINER_METADATA_COLLECTION)
        val containerMetadata: ContainerMetadata =
            containerMetadataCollection.findOne(eq(CONTAINER_NAME_FIELD, containerName))!!
        return Response.ok(containerMetadata.fieldCounts.toSortedMap()).build()
    }

    @Operation(description = "Get some container metadata")
    @Timed
    @GET
    @Path("{containerName}/metadata")
    fun getMetadataForContainer(
        @PathParam("containerName") containerName: String,
    ): Response {
        checkContainerExists(containerName)
        val container = mdb.getCollection(containerName)
        val containerMetadataStore = mdb.getCollection<ContainerMetadata>(ARConst.CONTAINER_METADATA_COLLECTION)
        val meta = containerMetadataStore.findOne { eq(CONTAINER_NAME_FIELD, containerName) }!!

        val metadata = mapOf(
            "id" to uriFactory.containerURL(containerName),
            "label" to meta.label,
            "created" to meta.createdAt,
            "modified" to meta.modifiedAt,
            "size" to container.countDocuments(),
            "indexes" to container.listIndexes(),
        )
        return Response.ok(metadata).build()
    }

    @Operation(description = "List a container's indexes")
    @Timed
    @GET
    @Path("{containerName}/indexes")
    fun getContainerIndexes(
        @PathParam("containerName") containerName: String,
    ): Response {
        checkContainerExists(containerName)
        val container = mdb.getCollection(containerName)
        val body = indexData(container, containerName)
        return Response.ok(body).build()
    }

    @Operation(description = "Add an index")
    @Timed
    @PUT
    @Path("{containerName}/indexes/{fieldName}/{indexType}")
    fun addContainerIndex(
        @PathParam("containerName") containerName: String,
        @PathParam("fieldName") fieldNameParam: String,
        @PathParam("indexType") indexTypeParam: String,
    ): Response {
        checkContainerExists(containerName)
        val container = mdb.getCollection(containerName)

        val fieldName = "$ANNOTATION_FIELD.${fieldNameParam}"
        val indexType =
            IndexType.fromString(indexTypeParam) ?: throw BadRequestException("Unknown indexType $indexTypeParam")
        val index = when (indexType) {
            IndexType.HASHED -> Indexes.hashed(fieldName)
            IndexType.ASCENDING -> Indexes.ascending(fieldName)
            IndexType.DESCENDING -> Indexes.descending(fieldName)
            IndexType.TEXT -> Indexes.text(fieldName)
            else -> throw RuntimeException("Cannot make an index with type $indexType")
        }
        val partialFilter = Filters.exists(fieldName)
        container.createIndex(index, IndexOptions().partialFilterExpression(partialFilter))
        val location = uriFactory.indexURL(containerName, fieldNameParam, indexTypeParam)
        return Response.created(location).build()
    }

    @Operation(description = "Get an index definition")
    @Timed
    @GET
    @Path("{containerName}/indexes/{fieldName}/{indexType}")
    fun getContainerIndexDefinition(
        @PathParam("containerName") containerName: String,
        @PathParam("fieldName") fieldName: String,
        @PathParam("indexType") indexType: String,
    ): Response {
        checkContainerExists(containerName)
        val container = mdb.getCollection(containerName)
        val indexConfig =
            getIndexConfig(container, containerName, fieldName, indexType)
        return Response.ok(indexConfig).build()
    }

    @Operation(description = "Delete a container index")
    @Timed
    @DELETE
    @Path("{containerName}/indexes/{fieldName}/{indexType}")
    fun deleteContainerIndex(
        @PathParam("containerName") containerName: String,
        @PathParam("fieldName") fieldName: String,
        @PathParam("indexType") indexType: String,
    ): Response {
        checkContainerExists(containerName)
        val container = mdb.getCollection(containerName)
        val indexConfig =
            getIndexConfig(container, containerName, fieldName, indexType)
        val indexName = "$ANNOTATION_FIELD.${indexConfig.field}_${indexConfig.type.mongoSuffix}"
        container.dropIndex(indexName)
        return Response.noContent().build()
    }

    private fun getIndexConfig(
        container: MongoCollection<Document>,
        containerName: String,
        fieldName: String,
        indexType: String,
    ): IndexConfig =
        indexData(container, containerName)
            .firstOrNull { it.field == fieldName && it.type == IndexType.fromString(indexType) }
            ?: throw NotFoundException()

    private fun indexData(container: MongoCollection<Document>, containerName: String): List<IndexConfig> =
        container.listIndexes()
            .map { it.toMap().asIndexConfig(containerName) }
            .filterNotNull()
            .toList()

    private fun Map<String, Any>.asIndexConfig(containerName: String): IndexConfig? {
        val name = this["name"].toString()
        val splitPosition = name.lastIndexOf("_")
        val field = name.subSequence(0, splitPosition).toString()
        val typeCode = name.subSequence(startIndex = splitPosition + 1, endIndex = name.lastIndex + 1).toString()
        val prefix = "$ANNOTATION_FIELD."
        return when {
            name.startsWith(prefix) -> {
                val type = when (typeCode) {
                    IndexType.HASHED.mongoSuffix -> IndexType.HASHED
                    IndexType.ASCENDING.mongoSuffix -> IndexType.ASCENDING
                    IndexType.DESCENDING.mongoSuffix -> IndexType.DESCENDING
                    IndexType.TEXT.mongoSuffix -> IndexType.TEXT
                    else -> throw Exception("unexpected index type: $typeCode in $name")
                }
                val fieldName = field.replace(prefix, "")
                IndexConfig(fieldName, type, uriFactory.indexURL(containerName, fieldName, type.name))
            }

            else -> null
        }
    }

    private fun getQueryCacheItem(searchId: String): QueryCacheItem = queryCache.getIfPresent(searchId)
        ?: throw NotFoundException("No search results found for this search id. The search might have expired.")

    private fun buildAnnotationPage(
        searchUri: URI, annotations: AnnotationList, page: Int, total: Int,
    ): AnnotationPage {
        val prevPage = if (page > 0) {
            page - 1
        } else {
            null
        }
        val startIndex = configuration.pageSize * page
        val nextPage = if (startIndex + annotations.size < total) {
            page + 1
        } else {
            null
        }

        return AnnotationPage(
            context = listOf(ANNO_JSONLD_URL),
            id = searchPageUri(searchUri, page),
            partOf = searchUri.toString(),
            startIndex = startIndex,
            items = annotations,
            prev = if (prevPage != null) searchPageUri(searchUri, prevPage) else null,
            next = if (nextPage != null) searchPageUri(searchUri, nextPage) else null
        )
    }

    private fun checkContainerExists(containerName: String) {
        if (!mdb.listCollectionNames().contains(containerName)) {
            throw BadRequestException("Annotation Container '$containerName' not found")
        }
    }

    private fun searchPageUri(searchUri: URI, page: Int) =
        UriBuilder.fromUri(searchUri).queryParam("page", page).build().toString()

    private fun toAnnotationMap(a: Document, containerName: String): Map<String, Any> =
        a.get(ANNOTATION_FIELD, Document::class.java)
            .toMutableMap()
            .apply<MutableMap<String, Any>> {
                put(
                    "id", uriFactory.annotationURL(containerName, a.getString(ANNOTATION_NAME_FIELD))
                )
            }
}

