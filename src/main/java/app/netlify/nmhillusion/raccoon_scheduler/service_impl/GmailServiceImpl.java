package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.n2mix.constant.OkHttpContentType;
import app.netlify.nmhillusion.n2mix.helper.http.HttpHelper;
import app.netlify.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import app.netlify.nmhillusion.n2mix.helper.log.LogHelper;
import app.netlify.nmhillusion.n2mix.type.ChainMap;
import app.netlify.nmhillusion.raccoon_scheduler.config.GmailConstant;
import app.netlify.nmhillusion.raccoon_scheduler.service.GmailService;
import org.springframework.stereotype.Service;

/**
 * date: 2022-11-20
 * <p>
 * created-by: nmhillusion
 */

@Service
public class GmailServiceImpl implements GmailService {
    private final HttpHelper httpHelper = new HttpHelper();

    @Override
    public void sendMail(String recipient, String subject, String body) throws Exception {
        final byte[] sendMailResponse = httpHelper.post(new RequestHttpBuilder()
                .setUrl(GmailConstant.getInstance().ENDPOINT_URL)
                .setBody(new ChainMap<String, Object>()
                                .chainPut("method", GmailConstant.getInstance().METHOD__SEND_MAIL)
                                .chainPut("data", new ChainMap<String, Object>()
                                        .chainPut("recipient", recipient)
                                        .chainPut("subject", subject)
                                        .chainPut("body", body)
                                )
                        , OkHttpContentType.JSON)

        );

        LogHelper.getLog(this).info("send mail response: " + new String(sendMailResponse));
    }
}
