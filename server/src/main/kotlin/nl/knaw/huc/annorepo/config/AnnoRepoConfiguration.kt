package nl.knaw.huc.annorepo.config

import com.fasterxml.jackson.annotation.JsonProperty
import `in`.vectorpro.dropwizard.swagger.SwaggerBundleConfiguration
import io.dropwizard.Configuration
import nl.knaw.huc.annorepo.api.ARConst
import nl.knaw.huc.annorepo.resources.AboutResource
import javax.validation.Valid
import javax.validation.constraints.NotNull

open class AnnoRepoConfiguration : Configuration() {

//    private val log = LoggerFactory.getLogger(javaClass)

    @Valid
    @NotNull
    @JsonProperty
    var prettyPrint: Boolean = true

    @Valid
    @NotNull
    @JsonProperty
    var mongodbURL: String = "mongodb://localhost/"

    @Valid
    @NotNull
    @JsonProperty
    var databaseName: String = "annorepo"

    @Valid
    @NotNull
    @JsonProperty
    var pageSize: Int = 100

    @Valid
    @NotNull
    @JsonProperty
    var externalBaseUrl = ""

    @Valid
    @NotNull
    @JsonProperty
    var rangeSelectorType = "urn:republic:TextAnchorSelector"

    @Valid
    @JsonProperty
    var withAuthentication: Boolean = false

    @Valid
    @NotNull
    @JsonProperty
    var rootApiKey: String = "YouIntSeenMeRoit"

    @Valid
    @NotNull
    @JsonProperty("swagger")
    val swaggerBundleConfiguration = SwaggerBundleConfiguration().apply {
        resourcePackage = AboutResource::class.java.getPackage().name
        version = javaClass.getPackage().implementationVersion
        title = ARConst.APP_NAME
        license = "Apache 2.0"
        licenseUrl = "http://www.apache.org/licenses/"
        contactUrl = "https://github.com/knaw-huc/annorepo"
        contact = ARConst.APP_NAME
    }

}