package nl.knaw.huc.annorepo.resources

import java.time.Instant

data class ContainerMetadata(val name: String, val label: String, val createdAt: Instant = Instant.now())