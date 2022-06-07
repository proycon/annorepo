package nl.knaw.huc.annorepo.resources

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import io.dropwizard.testing.junit5.ResourceExtension
import nl.knaw.huc.annorepo.api.ResourcePaths.MONGO
import nl.knaw.huc.annorepo.config.AnnoRepoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.eclipse.jetty.http.HttpStatus
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.litote.kmongo.KMongo
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType

private const val BASE_URI = "https://annorepo.com"

@ExtendWith(DropwizardExtensionsSupport::class)
class MongoResourceTest {

    private val client = KMongo.createClient("mongodb://localhost/")
    private val config: AnnoRepoConfiguration = AnnoRepoConfiguration().apply { externalBaseUrl = BASE_URI }
    private val resource = ResourceExtension.builder().addResource(MongoResource(client, config))
        .addResource(SearchResource(config, client)).build()

    @Disabled
    @Test
    fun test() {
        val name = "containername"
        val createResponse =
            resource.client().target("/$MONGO/").request(MediaType.APPLICATION_JSON).header("Slug", name).post(null)
        println("response=$createResponse")
        assertThat(createResponse.status).isEqualTo(HttpStatus.CREATED_201)
        println(
            createResponse.readEntity(HashMap::class.java)
        )
        val deleteResponse = resource.client().target("/$MONGO/$name").request(MediaType.APPLICATION_JSON).delete()
        assertThat(deleteResponse.status).isEqualTo(HttpStatus.NO_CONTENT_204)

        val readResponse = resource.client().target("/$MONGO/$name").request(MediaType.APPLICATION_JSON).get()
        println(readResponse.status)
        println(readResponse.readEntity(HashMap::class.java))
//        assertThat(readResponse.status).isEqualTo(HttpStatus.SC_NOT_FOUND)
    }

    @Disabled
    @Test
    fun testSearchAnnotations() {
        val name = "2b958f4-e66b-40dd-bf90-923849ec5540"
        val query = """
            {
                "target.selector.type": "urn:example:republic:TextAnchorSelector",
                "type": "Annotation"
            }
        """.trimIndent()
        val searchResponse = resource.client().target("/search/$name/annotations").request(MediaType.APPLICATION_JSON)
            .post(Entity.json(Document.parse(query)))
        println("response=$searchResponse")
        assertThat(searchResponse.status).isEqualTo(HttpStatus.OK_200)
        println(
            searchResponse.readEntity(String::class.java)
        )
    }

    @Disabled
    @Test
    fun testSearchAnnotationsWithinRange() {
        val name = "searchbyrange"
        val searchResponse =
            resource.client().target("/search/$name/within_range").queryParam("target.source", "urn:textrepo:text_x")
                .queryParam("range.start", 10).queryParam("range.end", 300).request().get()
        println("response=$searchResponse")
        assertThat(searchResponse.status).isEqualTo(HttpStatus.OK_200)
        val resultPage = searchResponse.readEntity(Document::class.java)
        val list = getAnnotationList(resultPage)
        println(list)
        assertThat(list).hasSize(2)
    }

    @Disabled
    @Test
    fun testSearchAnnotationsOverlappingWithRange() {
        val name = "searchbyrange"
        val searchResponse = resource.client().target("/search/$name/overlapping_with_range")
            .queryParam("target.source", "urn:textrepo:text_x").queryParam("range.start", 10)
            .queryParam("range.end", 300).request().get()
        println("response=$searchResponse")
        assertThat(searchResponse.status).isEqualTo(HttpStatus.OK_200)
        val resultPage = searchResponse.readEntity(Document::class.java)
        val list = getAnnotationList(resultPage)
        println(list)
        assertThat(list).hasSize(4)
    }

    private fun getAnnotationList(resultPage: Document): ArrayList<*> {
        return resultPage.get("as:items", Map::class.java)["@list"] as ArrayList<*>
    }
}