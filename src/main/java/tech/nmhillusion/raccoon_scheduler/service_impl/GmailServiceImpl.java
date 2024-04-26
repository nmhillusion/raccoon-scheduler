package tech.nmhillusion.raccoon_scheduler.service_impl;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import tech.nmhillusion.n2mix.constant.OkHttpContentType;
import tech.nmhillusion.n2mix.helper.http.HttpHelper;
import tech.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import tech.nmhillusion.n2mix.type.ChainMap;
import tech.nmhillusion.raccoon_scheduler.config.GmailConstant;
import tech.nmhillusion.raccoon_scheduler.entity.gmail.AttachmentEntity;
import tech.nmhillusion.raccoon_scheduler.entity.gmail.MailEntity;
import tech.nmhillusion.raccoon_scheduler.entity.gmail.SendEmailResponse;
import tech.nmhillusion.raccoon_scheduler.service.GmailService;

import java.util.stream.Collectors;

/**
 * date: 2022-11-20
 * <p>
 * created-by: nmhillusion
 */

@Service
public class GmailServiceImpl implements GmailService {
    private final HttpHelper httpHelper = new HttpHelper();

    @Override
    public SendEmailResponse sendMail(MailEntity mailEntity) throws Exception {
        final byte[] sendMailResponse = httpHelper.post(new RequestHttpBuilder()
                .setUrl(GmailConstant.getInstance().ENDPOINT_URL)
                .setBody(new ChainMap<String, Object>()
                                .chainPut("method", GmailConstant.getInstance().METHOD__SEND_MAIL)
                                .chainPut("data", new ChainMap<String, Object>()
                                        .chainPut("recipient", String.join(",", mailEntity.getRecipientMails()))
                                        .chainPut("subject", mailEntity.getSubject())
                                        .chainPut("body", mailEntity.getHtmlBody())
                                        .chainPut("cc", String.join(",", mailEntity.getCcMails()))
                                        .chainPut("bcc", String.join(",", mailEntity.getBccMails()))
                                        .chainPut("attachments", mailEntity.getAttachments().stream().map(AttachmentEntity::toJson).collect(Collectors.toList()))
                                )
                        , OkHttpContentType.JSON)

        );

        return SendEmailResponse.fromJSON(new JSONObject(new String(sendMailResponse)));
    }
}
