package io.openshift.booster;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import io.openshift.booster.test.OpenShiftTestAssistant;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hamcrest.core.Is.is;

/**
 * Check the behavior of the application when running in OpenShift.
 */
public class OpenShiftIT {

  private static OpenShiftTestAssistant assistant = new OpenShiftTestAssistant();

  @BeforeClass
  public static void prepare() throws Exception {
    assistant.deployApplication();

    // Deploy the database and wait until it's ready.
    assistant.deploy("database", new File("src/test/resources/templates/database.yml"));
    assistant.awaitPodReadinessOrFail(
        pod -> "my-database".equals(pod.getMetadata().getLabels().get("app"))
    );
    System.out.println("Database ready");

    assistant.deployApplication();

    assistant.awaitApplicationReadinessOrFail();

    await().atMost(5, TimeUnit.MINUTES).until(() -> {
      try {
        Response response = get();
        return response.getStatusCode() < 500;
      } catch (Exception e) {
        return false;
      }
    });

    RestAssured.baseURI = RestAssured.baseURI + "/api/products";

  }

  @AfterClass
  public static void cleanup() {
    assistant.cleanup();
  }


  @Before
  public void removeAllData() {
    String s = get().asString();
    JsonArray array = new JsonArray(s);
    for (int i = 0; i < array.size(); i++) {
      JsonObject json = array.getJsonObject(i);
      long id = json.getLong("id");
      delete("/" + id);
    }
  }

  @Test
  public void testRetrieveWhenNoFruits() {
    get()
      .then()
      .assertThat().statusCode(200).body(is("[ ]"));
  }

  @Test
  public void testWithOneFruit() {
    given()
      .body(new JsonObject().put("name", "iphone").put("stock", 5).encode())
      .post()
      .then().assertThat().statusCode(201);

    String payload = get()
      .then()
      .assertThat().statusCode(200).extract().asString();
    JsonArray json = new JsonArray(payload);
    assertThat(json).hasSize(1);
    assertThat(json.getJsonObject(0).getMap()).contains(entry("name", "iphone"), entry("stock", 5));
    Long id = json.getJsonObject(0).getLong("id");
    assertThat(id).isNotNull().isGreaterThanOrEqualTo(0);

    payload = get("/" + id).then().assertThat().statusCode(200).extract().asString();
    JsonObject obj = new JsonObject(payload);
    assertThat(obj.getMap()).contains(entry("name", "iphone"), entry("stock", 5));
    assertThat(obj.getLong("id")).isNotNull().isGreaterThanOrEqualTo(id);

  }

  @Test
  public void testCreatingAFruit() {
    Response response = given()
      .body(new JsonObject().put("name", "iphone").put("stock", 5).encode())
      .post()
      .then().assertThat().statusCode(201).extract().response();

    assertThat(response.header("Location")).isNotBlank();
    JsonObject result = new JsonObject(response.asString());
    assertThat(result.getLong("id")).isGreaterThanOrEqualTo(0);
    assertThat(result.getString("name")).isEqualTo("iphone");
    assertThat(result.getInteger("stock")).isEqualTo(5);

    String payload = get()
      .then()
      .assertThat().statusCode(200).extract().asString();
    JsonArray json = new JsonArray(payload);
    assertThat(json).hasSize(1);
    assertThat(json.getJsonObject(0).getMap()).contains(entry("name", "iphone"), entry("stock", 5));
    assertThat(json.getJsonObject(0).getLong("id")).isNotNull().isGreaterThanOrEqualTo(0);
  }

  @Test
  public void testCreatingAFruitWithoutAName() {
    Response response = given()
      .body(new JsonObject().put("stock", 5).encode())
      .post()
      .then().assertThat().statusCode(422).extract().response();

    JsonObject result = new JsonObject(response.asString());
    assertThat(result.getString("error")).isNotBlank();
    assertThat(result.getString("path")).isEqualTo("/api/products");
  }

  @Test
  public void testCreatingAFruitWithAnId() {
    Response response = given()
      .body(new JsonObject().put("stock", 5).put("name", "iphone").put("id", 2456).encode())
      .post()
      .then().assertThat().statusCode(422).extract().response();

    JsonObject result = new JsonObject(response.asString());
    assertThat(result.getString("error")).isNotBlank();
    assertThat(result.getString("path")).isEqualTo("/api/products");
  }

  @Test
  public void testCreatingWithNoPayload() {
    Response response = given()
      .body("")
      .post()
      .then().assertThat().statusCode(415).extract().response();

    JsonObject result = new JsonObject(response.asString());
    assertThat(result.getString("error")).isNotBlank();
    assertThat(result.getString("path")).isEqualTo("/api/products");
  }

  @Test
  public void testCreatingWithBrokenPayload() {
    Response response = given()
      .body("<name>iphone</name><stock>22</stock>")
      .post()
      .then().assertThat().statusCode(415).extract().response();

    JsonObject result = new JsonObject(response.asString());
    assertThat(result.getString("error")).isNotBlank();
    assertThat(result.getString("path")).isEqualTo("/api/products");
  }

  @Test
  public void testEditingAFruit() {
    Response response = given()
      .body(new JsonObject().put("name", "iphone").put("stock", 5).encode())
      .post()
      .then().assertThat().statusCode(201).extract().response();

    JsonObject result = new JsonObject(response.asString());
    long id = result.getLong("id");
    assertThat(id).isGreaterThanOrEqualTo(0);
    assertThat(result.getString("name")).isEqualTo("iphone");
    assertThat(result.getInteger("stock")).isEqualTo(5);

    response = given()
      .body(new JsonObject().put("name", "android").put("stock", 10).encode())
      .put("/" + id)
      .then().assertThat().statusCode(200).extract().response();

    result = new JsonObject(response.asString());
    assertThat(result.getLong("id")).isEqualTo(id);
    assertThat(result.getString("name")).isEqualTo("android");
    assertThat(result.getInteger("stock")).isEqualTo(10);

    String payload = get()
      .then()
      .assertThat().statusCode(200).extract().asString();
    JsonArray json = new JsonArray(payload);
    assertThat(json).hasSize(1);
    assertThat(json.getJsonObject(0).getMap()).contains(entry("id", (int) id), entry("name", "android"), entry("stock",
      10));
  }

  @Test
  public void testEditingAnUnknownFruit() {
    Response response = given()
      .body(new JsonObject().put("name", "android").put("stock", 10).encode())
      .put("/" + 22222222)
      .then().assertThat().statusCode(404).extract().response();

    JsonObject result = new JsonObject(response.asString());
    assertThat(result.getString("error")).isNotBlank();
    assertThat(result.getString("path")).isEqualTo("/api/products/22222222");
  }

  @Test
  public void testEditingAnUnknownFruitWithStringId() {
    Response response = given()
      .body(new JsonObject().put("name", "android").put("stock", 10).encode())
      .put("/999999")
      .then().assertThat().statusCode(404).extract().response();

    JsonObject result = new JsonObject(response.asString());
    assertThat(result.getString("error")).isNotBlank();
    assertThat(result.getString("path")).isEqualTo("/api/products/999999");
  }

  @Test
  public void testEditingAFruitWithEmptyPayload() {
    Response response = given()
      .body(new JsonObject().put("name", "iphone").put("stock", 5).encode())
      .post()
      .then().assertThat().statusCode(201).extract().response();

    JsonObject result = new JsonObject(response.asString());
    long id = result.getLong("id");

    response = given()
      .body("")
      .put("/" + id)
      .then().assertThat().statusCode(415).extract().response();

    result = new JsonObject(response.asString());
    assertThat(result.getString("error")).isNotBlank();
    assertThat(result.getString("path")).isEqualTo("/api/products/" + id);
  }

  @Test
  public void testEditingAFruitWithBrokenPayload() {
    Response response = given()
      .body(new JsonObject().put("name", "iphone").put("stock", 5).encode())
      .post()
      .then().assertThat().statusCode(201).extract().response();

    JsonObject result = new JsonObject(response.asString());
    long id = result.getLong("id");

    response = given()
      .body("{\"name\":\"android\", \"stock\":") // not complete on purpose.
      .put("/" + id)
      .then().assertThat().statusCode(415).extract().response();

    result = new JsonObject(response.asString());
    assertThat(result.getString("error")).isNotBlank();
    assertThat(result.getString("path")).isEqualTo("/api/products/" + id);
  }

  @Test
  public void testEditingAFruitWithInvalidPayload() {
    Response response = given()
      .body(new JsonObject().put("name", "iphone").put("stock", 5).encode())
      .post()
      .then().assertThat().statusCode(201).extract().response();

    JsonObject result = new JsonObject(response.asString());
    long id = result.getLong("id");

    response = given()
      .body(new JsonObject().put("name", "android").put("stock", 5).put("id", id + 1).encode())
      .put("/" + id)
      .then().assertThat().statusCode(422).extract().response();

    result = new JsonObject(response.asString());
    assertThat(result.getString("error")).isNotBlank();
    assertThat(result.getString("path")).isEqualTo("/api/products/" + id);
  }

  @Test
  public void testDeletingAFruit() {
    Response response = given()
      .body(new JsonObject().put("name", "iphone").put("stock", 5).encode())
      .post()
      .then().assertThat().statusCode(201).extract().response();

    JsonObject result = new JsonObject(response.asString());
    long id = result.getLong("id");

    delete("/" + id)
      .then().assertThat().statusCode(204);

    get()
      .then()
      .assertThat().statusCode(200).body(is("[ ]"));
  }

  @Test
  public void testDeletingAnUnknownFruit() {
    delete("/99999")
      .then().assertThat().statusCode(404);

    get()
      .then()
      .assertThat().statusCode(200).body(is("[ ]"));
  }

}
