package nl.knaw.huc.resources

import com.codahale.metrics.annotation.Timed
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


@Api("/")
@Path("/")
class HomePageResource {
    /**
     * Shows the homepage for the backend
     *
     * @return HTML representation of the homepage
     */
    @ApiOperation(value = "Show the server homepage")
    @Produces(MediaType.TEXT_HTML)
    @Timed
    @GET
    fun getHomePage(): Response {
        val resourceAsStream = Thread.currentThread().contextClassLoader.getResourceAsStream("index.html")
        return Response.ok(resourceAsStream)
            .header("Pragma", "public")
            .header("Cache-Control", "public")
            .build()
    }

    @ApiOperation(value = "Placeholder for favicon.ico")
    @Path("favicon.ico")
    @GET
    fun getFavIcon(): Response = Response.noContent().build()

    @GET
    @Path("robots.txt")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiOperation(value = "Placeholder for robots.txt")
    fun noRobots(): String {
        return "User-agent: *\nDisallow: /\n"
    }
}
