package tech.nmhillusion.raccoon_scheduler.helper;

import tech.nmhillusion.n2mix.helper.http.HttpHelper;
import tech.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import tech.nmhillusion.n2mix.helper.log.LogHelper;

/**
 * created by: nmhillusion
 * <p>
 * created date: 2024-05-13
 */
public class OnRenderHelper {
    private final static int MAX_RETRY = 3;
    private final static int WAITING_TIME_IN_MILLIS = 1000 * 45;
    private final static String SUCCESS_RESPONSE = "server Ok";
    private final static String ONRENDER_CHECKING_URL = "https://nmhillusion.onrender.com/";

    public boolean wakeup() throws InterruptedException {
        boolean result = false;

        final HttpHelper httpHelper = new HttpHelper();
        for (int retryIdx = 0; retryIdx < MAX_RETRY; ++retryIdx) {
            try {
                final byte[] response_ = httpHelper.get(
                        new RequestHttpBuilder()
                                .setUrl(ONRENDER_CHECKING_URL)
                );

                if (SUCCESS_RESPONSE.equals(new String(response_))) {
                    result = true;
                    break;
                } else {
                    throw new Exception("response: " + new String(response_));
                }
            } catch (Exception e) {
                LogHelper.getLogger(this).error(e);
                Thread.sleep(WAITING_TIME_IN_MILLIS);
            }
        }

        return result;
    }

}
