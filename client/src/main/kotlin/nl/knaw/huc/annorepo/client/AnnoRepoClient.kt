package nl.knaw.huc.annorepo.client

import nl.knaw.huc.annorepo.util.extractVersion
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204
import org.slf4j.LoggerFactory
import java.net.URI
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.Response

class AnnoRepoClient(serverURI: URI, private val userAgent: String? = null) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webTarget: WebTarget = ClientBuilder.newClient()
        .target(serverURI)

    fun getAbout(): Map<String, Any> =
        webTarget.path("about")
            .request()
            .addUserAgent()
            .get(Map::class.java) as Map<String, Any>

    fun createContainer(
        preferredName: String? = null,
        label: String = "A container for web annotations"
    ): AnnoRepoResponse {
        var request = webTarget.path("w3c").request()
        if (preferredName != null) {
            request = request.header("slug", preferredName)
        }
        val specs = containerSpecs(label)
        val response = request.addUserAgent()
            .post(Entity.json(specs))
        log.info("response={}", response)
        val created = (response.status == HttpStatus.CREATED_201)
        val location = location(response) ?: ""
        val containerId = extractId(location)
        val etag = eTag(response) ?: ""
        return AnnoRepoResponse(created, location, containerId = containerId, etag = etag)
    }

    private fun extractId(location: String): String {
        val parts = location.split("/")
        return parts[parts.size - 2]
    }

    fun deleteContainer(containerName: String, etag: String): Boolean {
        val response = webTarget.path("w3c").path(containerName)
            .request()
            .header("if-match", etag)
            .addUserAgent()
            .delete()
        log.info("{}", response)
        return response.status == NO_CONTENT_204
    }

    private fun location(response: Response): String? =
        response.firstHeader("location")

    private fun eTag(response: Response): String? =
        response.firstHeader("etag")

    private fun Response.firstHeader(key: String): String? =
        if (headers.containsKey(key)) {
            val locations: MutableList<Any> = headers[key]!!
            locations[0].toString()
        } else {
            null
        }

    private fun Invocation.Builder.addUserAgent(): Invocation.Builder {
        val libUA = "${AnnoRepoClient::class.java.name}/${getVersion() ?: ""}"
        val ua = if (userAgent == null) {
            libUA
        } else {
            "$userAgent ( using $libUA )"
        }
        return header("User-Agent", ua)
    }

    private fun containerSpecs(label: String) = mapOf(
        "@context" to listOf(
            "http://www.w3.org/ns/anno.jsonld",
            "http://www.w3.org/ns/ldp.jsonld"
        ),
        "type" to listOf(
            "BasicContainer",
            "AnnotationCollection"
        ),
        "label" to label
    )

    private fun getVersion(): String? =
        this.javaClass.extractVersion()

}