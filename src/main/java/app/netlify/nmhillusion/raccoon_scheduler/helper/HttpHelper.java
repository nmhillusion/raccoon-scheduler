package app.netlify.nmhillusion.raccoon_scheduler.helper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

import static app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper.getLog;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */

@Component
public class HttpHelper {
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .callTimeout(Duration.ofMinutes(2))
            .connectTimeout(Duration.ofMinutes(2))
            .readTimeout(Duration.ofMinutes(2))
            .writeTimeout(Duration.ofMinutes(2))
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(chain -> {
                getLog(this).debug("http interceptor: " + chain);
                return chain.proceed(chain.request());
            })
            .hostnameVerifier((hostname, sslSession) -> true)
            .build();

    public byte[] get(String url) throws IOException {
        try (
                final Response execute = okHttpClient.newCall(new Request.Builder()
                        .get()
                        .url(url)
                        .build()
                ).execute()
        ) {
            try (final ResponseBody responseBody = execute.body()) {
                if (responseBody != null) {
                    return responseBody.bytes();
                } else {
                    return new byte[0];
                }
            }
        }
    }
}
