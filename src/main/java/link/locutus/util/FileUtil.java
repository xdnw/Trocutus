package link.locutus.util;

import link.locutus.core.settings.Settings;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class FileUtil {
    private static final String COOKIES_HEADER = "Set-Cookie";

    public static String readFile(String name) {
        try (InputStream resource = FileUtil.class.getResourceAsStream(name)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = resource.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            byte[] bytes = buffer.toByteArray();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<String> getResourceFiles(String path) throws IOException {
        List<String> filenames = new ArrayList<>();

        try (
                InputStream in = getResourceAsStream(path);
                BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String resource;

            while ((resource = br.readLine()) != null) {
                filenames.add(resource);
            }
        }

        return filenames;
    }

    public static InputStream getResourceAsStream(String resource) {
        final InputStream in
                = getContextClassLoader().getResourceAsStream(resource);

        return in == null ? FileUtil.class.getResourceAsStream(resource) : in;
    }

    public static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    public static byte[] readBytesFromUrl(String urlStr) {
        try (InputStream is = new URL(urlStr).openStream()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] byteChunk = new byte[4096]; // Or whatever size you want to read in at a time.
            int n;

            while ( (n = is.read(byteChunk)) > 0 ) {
                baos.write(byteChunk, 0, n);
            }
            is.close();
            return baos.toByteArray();
        }
        catch (IOException e) {
            e.printStackTrace ();
            return null;
        }
    }

    public static String readStringFromURL(String requestURL) throws IOException {
        URL website = new URL(requestURL);
        URLConnection connection = website.openConnection();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {

            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        }
    }

    public static String encode(String url) throws UnsupportedEncodingException {
        String[] split = url.split("\\?", 2);
        if (split.length == 1) return url;
        return split[0] + "?" + URLEncoder.encode(split[1], StandardCharsets.UTF_8);
    }

    public static CompletableFuture<String> readStringFromURL(int priority, String urlStr, Map<String, String> arguments) throws IOException {
        return readStringFromURL(priority, urlStr, arguments, null);
    }

    public static CompletableFuture<String> readStringFromURL(int priority, String urlStr, Map<String, String> arguments, CookieManager msCookieManager) throws IOException {
        return readStringFromURL(priority, urlStr, arguments, true, msCookieManager, i -> {});
    }

    public enum RequestType {
        GET,
        POST,
        HEAD,
    }

    private static AtomicInteger requestOrder = new AtomicInteger();
    private static long lastRead = 0;

    public static CompletableFuture<String> readStringFromURL(int priority, String urlStr, byte[] dataBinary, RequestType type, CookieManager msCookieManager, Consumer<HttpURLConnection> apply) {
        long orderedPriority = requestOrder.incrementAndGet() + Integer.MAX_VALUE + priority;
        PageRequestQueue.PageRequestTask<String> task = pageRequestQueue.submit(new Supplier<String>() {
            @Override
            public String get() {
                long now = System.currentTimeMillis();
                System.out.println("Requesting " + urlStr + " at " + now + " with priority " + priority + " ( last: " + (now - lastRead) + " )");
                lastRead = now;
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection http = (HttpURLConnection) url.openConnection();

                    if (msCookieManager != null && msCookieManager.getCookieStore().getCookies().size() > 0) {
                        List<String> cookies = new ArrayList<>();
                        for (HttpCookie cookie : msCookieManager.getCookieStore().getCookies()) {
                            if (cookie.getName().equalsIgnoreCase("XSRF-TOKEN")) {
                                // x-requested-with: XMLHttpRequest
                                http.setRequestProperty("x-requested-with", "XMLHttpRequest");
                                http.setRequestProperty("x-xsrf-token", URLDecoder.decode(cookie.getValue()));
                            }
                            cookies.add(cookie.getName() + "=" + cookie.getValue());
                        }
                        if (!cookies.isEmpty()) {
                            http.addRequestProperty("cookie", String.join(";", cookies));
                        }
                    }

                    http.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
                    http.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
                    http.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                    http.setRequestProperty("Referer", urlStr);
                    http.setRequestProperty("dnt", "1");

                    http.setRequestProperty("User-Agent", Settings.USER_AGENT);
                    switch (type) {
                        case GET:
                            http.setRequestMethod("GET");
                            http.setRequestProperty("content-type", "application/json");
                            break;
                        case POST:
                            http.setRequestMethod("POST");

                            http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                            http.setRequestProperty("Connection", "keep-alive");
                            int length = dataBinary != null ? dataBinary.length : 0;
                            http.setFixedLengthStreamingMode(length);
                            break;
                        case HEAD:
                            http.setRequestMethod("HEAD");
                            break;
                    }

                    http.setInstanceFollowRedirects(false);
                    http.setDoOutput(true);

                    if (apply != null) apply.accept(http);

                    if (dataBinary != null && dataBinary.length != 0) {
                        try (OutputStream os = http.getOutputStream()) {
                            os.write(dataBinary);
                        }
                    }

                    try (InputStream is = http.getInputStream()) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        int nRead;
                        byte[] data = new byte[8192];
                        while ((nRead = is.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }

                        buffer.flush();
                        byte[] bytes = buffer.toByteArray();

                        Map<String, List<String>> headerFields = http.getHeaderFields();

                        if (msCookieManager != null) {
                            for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
                                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(COOKIES_HEADER)) {
                                    List<String> cookiesHeader = entry.getValue();
                                    for (String cookie : cookiesHeader) {
                                        List<HttpCookie> parsed = HttpCookie.parse(cookie);
                                        for (HttpCookie httpCookie : parsed) {
                                            msCookieManager.getCookieStore().add(null, httpCookie);
                                        }
                                    }
                                }
                            }
                        }

                        return new String(bytes, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        e.printStackTrace();

                        try (InputStream is = http.getErrorStream()) {
                            throw new IOException(e.getMessage() + ":\n" + is == null ? "null" : IOUtils.toString(is, StandardCharsets.UTF_8));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }, priority);
        return task;
    }

    private static PageRequestQueue pageRequestQueue = new PageRequestQueue(4000);

    public static CompletableFuture<String> readStringFromURL(int priority, String urlStr, Map<String, String> arguments, boolean post, CookieManager msCookieManager, Consumer<HttpURLConnection> apply) throws IOException {
        StringJoiner sj = new StringJoiner("&");
        for (Map.Entry<String, String> entry : arguments.entrySet())
            sj.add(URLEncoder.encode(entry.getKey(), "UTF-8") + "="
                    + URLEncoder.encode(entry.getValue(), "UTF-8"));
        byte[] out = sj.toString().getBytes(StandardCharsets.UTF_8);
        return readStringFromURL(priority, urlStr, out, post ? RequestType.POST : RequestType.GET, msCookieManager, apply);
    }

    public static <T> T get(Future<T> myFuture) {
        try {
            return myFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}

