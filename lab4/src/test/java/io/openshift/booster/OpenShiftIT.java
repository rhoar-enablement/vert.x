package io.openshift.booster;

import com.jayway.restassured.response.Response;
import io.openshift.booster.test.OpenShiftTestAssistant;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.net.MalformedURLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.restassured.RestAssured.get;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OpenShiftIT {

    private static OpenShiftTestAssistant assistant = new OpenShiftTestAssistant();

    @BeforeClass
    public static void prepare() throws Exception {
        assistant.deployApplication();

        assistant.awaitApplicationReadinessOrFail();
        // Check that the route is served.
        await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> get().getStatusCode() < 400);
        await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> {
            try {
                return get("/api/greeting").getStatusCode() < 400;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @AfterClass
    public static void cleanup() {
        assistant.cleanup();
    }

    @Test
    public void testThatWeRecover() throws MalformedURLException {
        await().atMost(5, TimeUnit.MINUTES).catchUncaughtExceptions().until(() -> {
            try {
                get("/api/greeting").then().body("content", equalTo("Hello, World!"));
                return true;
            } catch (Exception e) {
                return false;
            }
        });


        // Kill me
        get("/api/killme").then().statusCode(200);

        AtomicInteger counter = new AtomicInteger();
        long begin = System.currentTimeMillis();
        await().atMost(5, TimeUnit.MINUTES).until(() -> {
            counter.incrementAndGet();
            Response response = get("/api/greeting");
            return response.getStatusCode() == 200;
        });

        // We recovered !
        long end = System.currentTimeMillis();
        System.out.println("Recovering failures in " + (end - begin) + " ms");
        System.out.println("Counter: " + counter.get());

    }

}
