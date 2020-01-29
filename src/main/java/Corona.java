import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Calendar;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Corona {
    private static final URI dataEndpoint = URI.create("https://services1.arcgis.com/0MSEUqKaxRlEPj5g/arcgis/rest/services/ncov_cases/FeatureServer/1/query?f=json&where=1%3D1&returnGeometry=false&spatialRel=esriSpatialRelIntersects&outStatistics=%5B%7B%22statisticType%22%3A%22sum%22%2C%22onStatisticField%22%3A%22Confirmed%22%2C%22outStatisticFieldName%22%3A%22confirmed%22%7D%2C%20%7B%22statisticType%22%3A%22sum%22%2C%22onStatisticField%22%3A%22Deaths%22%2C%22outStatisticFieldName%22%3A%22deaths%22%7D%2C%20%7B%22statisticType%22%3A%22sum%22%2C%22onStatisticField%22%3A%22Recovered%22%2C%22outStatisticFieldName%22%3A%22recovered%22%7D%5D&outSR=102100&cacheHint=false");
    private static final HttpRequest dataRequest = HttpRequest.newBuilder().uri(dataEndpoint).GET().build();
    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private static final Gson parser = new Gson();

    private static HttpRequest webhookRequest(URI uri, Stats stats) {
        var webhook = new Webhook();
        var embed = new Embed();
        webhook.avatar_url = "https://upload.wikimedia.org/wikipedia/en/b/b3/Plague_Inc._app_icon.png";
        webhook.username = "Coronavirus Stats";
        embed.color = 0xabcdef;
        embed.title = Calendar.getInstance().getTime().toString();
        embed.description = "We're all going to die from a fucking cold";
        var confirmed = new EmbedFields("Confirmed", String.valueOf(stats.confirmed));
        var deaths = new EmbedFields("Deaths", String.valueOf(stats.deaths));
        var recovered = new EmbedFields("Recovered", String.valueOf(stats.recovered));
        embed.fields = new EmbedFields[]{confirmed, deaths, recovered};
        webhook.embeds = new Embed[]{embed};
        var serialized = parser.toJson(webhook);
        var data = HttpRequest.BodyPublishers.ofString(serialized);
        return HttpRequest.newBuilder().uri(uri).POST(data).header("Content-Type", "application/json").build();
    }

    private static Stats coronaVirus() throws IOException, InterruptedException {
        var res = client.send(dataRequest, HttpResponse.BodyHandlers.ofString()).body();
        var data = parser.fromJson(res, DataResponse.class);
        return data.features[0].attributes;
    }

    private static Stream<CompletableFuture<HttpResponse<String>>> sendStats(Stats stats) {
        var hooksResource = Corona.class.getClassLoader().getResource("webhooks.txt");
        if (hooksResource == null) {
            return Stream.empty();
        }
        Stream<String> urls;
        try {
            urls = Files.readAllLines(Paths.get(hooksResource.getFile())).stream();
        } catch (IOException e) {
            e.printStackTrace();
            return Stream.empty();
        }
        return urls.map(webhook -> {
            var req = webhookRequest(URI.create(webhook), stats);
            return client.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        });
    }

    public static void main(String[] args) {
        try {
            var data = coronaVirus();
            AtomicInteger count = new AtomicInteger();
            sendStats(data).forEach(future -> {
                try {
                    future.get();
                    count.getAndIncrement();
                    System.out.println("Sent data: " + Calendar.getInstance().getTime());
                } catch (ExecutionException | InterruptedException ex) {
                    ex.printStackTrace();
                }
            });
            System.out.println("Sent all webhooks: " + count.getAcquire() + " requests total");
            System.exit(0);
        } catch (Exception err) {
            err.printStackTrace();
            System.out.println("Oops something happened");
        }
    }
}
