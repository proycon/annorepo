package nl.knaw.huc.annorepo.resources.tools

import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import net.javacrumbs.jsonunit.assertj.assertThatJson
import nl.knaw.huc.annorepo.config.AnnoRepoConfiguration
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.litote.kmongo.json
import org.slf4j.LoggerFactory
import javax.ws.rs.BadRequestException

@ExtendWith(MockKExtension::class)
class AggregateStageGeneratorTest {
    private val log = LoggerFactory.getLogger(javaClass)

    @RelaxedMockK
    lateinit var config: AnnoRepoConfiguration

    @Test
    fun `query keys should be strings`() {
//        every { config.rangeSelectorType } returns "something"
        val asg = AggregateStageGenerator(config)
        try {
            asg.generateStage(1, 2)
            fail("expected BadRequestException")
        } catch (bre: BadRequestException) {
            log.info(bre.toString())
            assertThat(bre.message).isEqualTo("Unexpected field: '1' ; query root fields should be strings")

        }
    }

    @Test
    fun `query keys should not be lists`() {
//        every { config.rangeSelectorType } returns "something"
        val asg = AggregateStageGenerator(config)
        try {
            asg.generateStage(listOf("field1", "field2"), "yes")
            fail("expected BadRequestException")
        } catch (bre: BadRequestException) {
            log.info(bre.toString())
            assertThat(bre.message)
                .isEqualTo("Unexpected field: '[field1, field2]' ; query root fields should be strings")
        }
    }

    @Test
    fun `query key starting with colon should be a defined query function`() {
        val asg = AggregateStageGenerator(config)
        try {
            asg.generateStage(":myQueryFunction", mapOf("parameter1" to "value1"))
            fail("expected BadRequestException")
        } catch (bre: BadRequestException) {
            log.info(bre.toString())
            assertThat(bre.message).isEqualTo("Unknown query function: ':myQueryFunction'")
        }
    }

    @Test
    fun `query functions - within_range`() {
        val selector = "rangeSelectorType"
        every { config.rangeSelectorType } returns selector
        val asg = AggregateStageGenerator(config)

        val source = "https://google.com"
        val start = 100
        val end = 200
        val parameters = mapOf(
            "source" to source,
            "start" to start,
            "end" to end
        )
        val stage = asg.generateStage(WITHIN_RANGE, parameters)
        log.info("{}", stage)
        val expected = """
            {
               "@match": {
                  "@and": [
                     { "annotation.target.source": "$source"},
                     { "annotation.target.selector.type": "$selector"},
                     { "annotation.target.selector.start": { "@gte": ${start.toFloat()} } },
                     { "annotation.target.selector.end":   { "@lte": ${end.toFloat()} } }
                  ]
               }
            }""".trimIndent().replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
        log.info("{}", stage.json)
    }

    @Test
    fun `simple field matching with string value`() {
        val asg = AggregateStageGenerator(config)
        val key = "body.type"
        val value = "Match"
        val stage = asg.generateStage(key, value)
        log.info("{}", stage)
        val expected = """
            { "@match": { "annotation.$key": "$value" } }
            """.trimIndent()
            .replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
        log.info("{}", stage.json)
    }

    @Test
    fun `simple field matching with number value`() {
        val asg = AggregateStageGenerator(config)
        val key = "body.count"
        val value = 42
        val stage = asg.generateStage(key, value)
        log.info("{}", stage)
        val expected = """
            { "@match": { "annotation.$key": $value } }
            """.trimIndent()
            .replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
        log.info("{}", stage.json)
    }

    @Test
    fun `special field matching - isNotIn`() {
        val asg = AggregateStageGenerator(config)
        val key = "year"
        val value = mapOf(IS_NOT_IN to arrayOf(2020, 2021, 2022, 2023))
        val stage = asg.generateStage(key, value)
        log.info("{}", stage)
        log.info("{}", stage.json)
        val expected = """
            { "@match": { "annotation.$key": { "@nin": [2020,2021,2022,2023] } } }
            """.trimIndent()
            .replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
    }

    @Test
    fun `special field matching - isIn`() {
        val asg = AggregateStageGenerator(config)
        val key = "year"
        val value = mapOf(IS_IN to arrayOf(2020, 2021, 2022, 2023))
        val stage = asg.generateStage(key, value)
        log.info("{}", stage)
        log.info("{}", stage.json)
        val expected = """
            { "@match": { "annotation.$key": { "@in": [2020,2021,2022,2023] } } }
            """.trimIndent()
            .replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
    }

    @Test
    fun `special field matching - isGreater`() {
        val asg = AggregateStageGenerator(config)
        val key = "year"
        val value = mapOf(IS_GREATER to 2000)
        val stage = asg.generateStage(key, value)
        log.info("{}", stage)
        log.info("{}", stage.json)
        val expected = """
            { "@match": { "annotation.$key": { "@gt": 2000 } } }
            """.trimIndent()
            .replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
    }

    @Test
    fun `special field matching - isGreaterOrEqual`() {
        val asg = AggregateStageGenerator(config)
        val key = "year"
        val value = mapOf(IS_GREATER_OR_EQUAL to 2000)
        val stage = asg.generateStage(key, value)
        log.info("{}", stage)
        log.info("{}", stage.json)
        val expected = """
            { "@match": { "annotation.$key": { "@gte": 2000 } } }
            """.trimIndent()
            .replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
    }

    @Test
    fun `special field matching - isLess`() {
        val asg = AggregateStageGenerator(config)
        val key = "year"
        val value = mapOf(IS_LESS to 2000)
        val stage = asg.generateStage(key, value)
        log.info("{}", stage)
        log.info("{}", stage.json)
        val expected = """
            { "@match": { "annotation.$key": { "@lt": 2000 } } }
            """.trimIndent()
            .replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
    }

    @Test
    fun `special field matching - isLessOrEqual`() {
        val asg = AggregateStageGenerator(config)
        val key = "year"
        val value = mapOf(IS_LESS_OR_EQUAL to 2000)
        val stage = asg.generateStage(key, value)
        log.info("{}", stage)
        log.info("{}", stage.json)
        val expected = """
            { "@match": { "annotation.$key": { "@lte": 2000 } } }
            """.trimIndent()
            .replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
    }

    @Test
    fun `special field matching - isEqualTo`() {
        val asg = AggregateStageGenerator(config)
        val key = "year"
        val value = mapOf(IS_EQUAL_TO to 2000)
        val stage = asg.generateStage(key, value)
        log.info("{}", stage)
        log.info("{}", stage.json)
        val expected = """
            { "@match": { "annotation.$key": 2000 } }
            """.trimIndent()
            .replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
    }

    @Test
    fun `special field matching - isNot`() {
        val asg = AggregateStageGenerator(config)
        val key = "year"
        val value = mapOf(IS_NOT to 2000)
        val stage = asg.generateStage(key, value)
        log.info("{}", stage)
        log.info("{}", stage.json)
        val expected = """
            { "@match": { "annotation.$key": { "@ne": 2000 } } }
            """.trimIndent()
            .replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
    }

    @Test
    fun `query functions - overlapping_with_range`() {
        val selector = "rangeSelectorType"
        every { config.rangeSelectorType } returns selector
        val asg = AggregateStageGenerator(config)
        val source = "http://example.com/some-id"
        val start = 200
        val end = 300
        val parameters = mapOf(
            "source" to source,
            "start" to start,
            "end" to end
        )
        val stage = asg.generateStage(OVERLAPPING_WITH_RANGE, parameters)
        log.info("{}", stage)
        val expected = """
            {
               "@match": {
                  "@and": [
                     { "annotation.target.source": "$source"},
                     { "annotation.target.selector.type": "$selector"},
                     { "annotation.target.selector.start": { "@lt": ${end.toFloat()} } },
                     { "annotation.target.selector.end":   { "@gt": ${start.toFloat()} } }
                  ]
               }
            }""".trimIndent().replace('@', '$')
        assertThatJson(stage.json).isEqualTo(expected)
        log.info("{}", stage.json)

    }
}