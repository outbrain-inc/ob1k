package com.outbrain.ob1k.crud.dao

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.outbrain.ob1k.crud.CrudApplication
import com.outbrain.ob1k.db.BasicDao
import com.outbrain.ob1k.db.MySqlConnectionPoolBuilder
import org.junit.After
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

//@Ignore
class MySQLCrudDaoTest {

    private val application = crudApplication()
    private val personDao = application.newMySQLDao("obcp_crud_person")
    private val jobDao = application.newMySQLDao("obcj_crud_job")
    private val email = "${Random().nextInt()}@outbrain.com"
    private val jsonParser = JsonParser()

    @After
    fun tearDown() {
        jobDao.list().andThen({ it.value.data.forEach { jobDao.delete(it.id()).get() } }).get()
        personDao.list().andThen({ it.value.data.forEach { personDao.delete(it.id()).get() } }).get()
        //personDao.list(filter = jsonParser.parse("{\"email\": \"$email\"}").asJsonObject).andThen({ it.value.data.forEach { personDao.delete(it.get("id").asInt).get() } }).get()
    }

    @org.junit.Test
    internal fun `create person`() {
        val json = person("create")
        val created = personDao.create(json).get()
        val id = created.id()
        assert(id > 0)
        created.addProperty("id", id)
        assertEqualsJson(json, created)
    }

    @org.junit.Test
    internal fun `update person`() {
        val created = personDao.create(person("update")).get()
        created.addProperty("alive", false)
        val updated = personDao.update(created.id(), created).get()
        assertEquals(false, updated.get("alive").asBoolean)
    }

    @org.junit.Test
    internal fun `read person`() {
        val created = personDao.create(person("read")).get()
        val read = personDao.read(created.get("id").asInt).get()
        assertEqualsJson(created, read!!)
    }

    @org.junit.Test
    internal fun `delete person`() {
        val created = personDao.create(person("delete")).get()
        val id = created.id()
        personDao.delete(id).get()
        assertNull(personDao.read(id).get())
    }

    @org.junit.Test
    internal fun `sort persons`() {
        (8 downTo 1).forEach { personDao.create(person("${it}sort")).get() }
        val entities1 = personDao.list(sort = "name" to "ASC").get()
        (0..entities1.data.size - 2).forEach {
            val name1 = entities1.data[it].get("name").asString
            val name2 = entities1.data[it + 1].get("name").asString
            assertTrue(name1 < name2)
        }
        val entities2 = personDao.list(sort = "id" to "DESC").get()
        (0..entities2.data.size - 2).forEach {
            val id1 = entities2.data[it].id()
            val id2 = entities2.data[it + 1].id()
            assertTrue(id1 > id2)
        }
    }

    @org.junit.Test
    internal fun `filter persons`() {
        (1..5).forEach { personDao.create(person("${it}filter1")).get() }
        (1..5).forEach { personDao.create(person("${it}filter2")).get() }
        assertEquals(10, personDao.list(filter = jsonParser.parse("{\"name\": \"filter\",\"email\": \"$email\"}").asJsonObject).get().data.size)
        assertEquals(5, personDao.list(filter = jsonParser.parse("{\"name\": \"filter1\",\"email\": \"$email\"}").asJsonObject).get().data.size)
    }

    @org.junit.Test
    internal fun `paginate persons`() {
        (1..10).forEach { personDao.create(person("paging")).get() }
        val entities = personDao.list(pagination = 0..5).get()
        assertEquals(6, entities.data.size)
        assertTrue { entities.total > entities.data.size }
    }


    private fun crudApplication() = CrudApplication(BasicDao(MySqlConnectionPoolBuilder
            .newBuilder("devdb-env.il.outbrain.com", 3307, "d_crudexample")
            .password("DbTWIZl7kf")
            .forDatabase("outbrain")
            .build()), "obcp_crud_person,obcj_crud_job")

    private fun assertEqualsJson(expected: JsonObject, actual: JsonObject) = expected.entrySet().map { it.key }.forEach { assertEquals(expected.get(it), actual.get(it)) }

    @Test
    fun manyToOneReferences() {
        val application = crudApplication()

        val personDao = application.newMySQLDao("obcp_crud_person")
        val jobDao = application.newMySQLDao("obcj_crud_job")

        val id0 = personDao.create(person("manyToOne")).get().id()
        val id1 = personDao.create(person("manyToOne")).get().id()

        var job0 = jobDao.create(job("manyToOne", id0)).get()

        job0 = jobDao.read(job0.id()).get()!!
        assertEquals(id0, job0.int("person"))

        job0.addProperty("person", id1)
        job0 = jobDao.update(job0.id(), job0).get()
        assertEquals(id1, job0.int("person"))

        assertFails { personDao.delete(id1).get() }

        jobDao.create(job("manyToOne", id0)).get()
        jobDao.create(job("manyToOne", id1)).get()

        assertEquals(2, jobDao.list(filter = JsonObject().with("person", id1)).get().data.size)
    }

    private fun person(testcase: String) = JsonObject()
            .with("name", "$testcase${Random().nextInt()}")
            .with("alive", true)
            .with("email", email)


    private fun job(testcase: String, personId: Int) = JsonObject()
            .with("company", "$testcase${Random().nextInt()}")
            .with("title", "QA")
            .with("person", personId)


    private fun JsonObject.with(name: String, value: Int): JsonObject {
        addProperty(name, value)
        return this
    }

    private fun JsonObject.with(name: String, value: String): JsonObject {
        addProperty(name, value)
        return this
    }

    private fun JsonObject.with(name: String, value: Boolean): JsonObject {
        addProperty(name, value)
        return this
    }

    private fun JsonObject.id() = int("id")
    private fun JsonObject.int(name: String) = get(name).asInt
}