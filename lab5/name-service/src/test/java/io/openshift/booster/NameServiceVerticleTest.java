package io.openshift.booster;

import static org.hamcrest.Matchers.is;

import static com.jayway.awaitility.Awaitility.await;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import io.restassured.RestAssured;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class NameServiceVerticleTest {


    private Vertx vertx;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        vertx.deployVerticle(NameServiceVerticle.class.getName());

        RestAssured.baseURI = "http://localhost:8080";

        await().until(() -> {
            try {
                return get("/health").statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @After
    public void tearDown() {
        AtomicBoolean closed = new AtomicBoolean();
        vertx.close(x -> closed.set(x.succeeded()));
        await().untilAtomic(closed, is(true));
    }

    @Test
    public void testHealth() {
        get("/health").then().statusCode(200);
    }

    @Test
    public void testNameWhenOk() {
        get("/api/name").then().statusCode(200).body("name", is(NameServiceVerticle.NAME));
    }

    @Test
    public void testNameWhenFailAndThenToggle() {
        given()
            .body(new JsonObject().put("state", "fail").encode())
            .when()
            .put("/api/state")
            .then()
            .statusCode(200)
            .body("state", is("fail"));

        get("/api/state").then().statusCode(200).body("state", is("fail"));
        get("/api/name").then()
            .statusCode(500);

        given()
            .body(new JsonObject().put("state", "ok").encode())
            .when()
            .put("/api/state")
            .then()
            .statusCode(200)
            .body("state", is("ok"));

        get("/api/state").then().statusCode(200).body("state", is("ok"));
        get("/api/name").then().statusCode(200).body("name", is(NameServiceVerticle.NAME));
    }


}