package tech.nmhillusion.raccoon_scheduler.service;

import tech.nmhillusion.raccoon_scheduler.entity.gmail.MailEntity;
import tech.nmhillusion.raccoon_scheduler.entity.gmail.SendEmailResponse;

/**
 * date: 2022-11-20
 * <p>
 * created-by: nmhillusion
 */
public interface GmailService {
    SendEmailResponse sendMail(MailEntity mailEntity) throws Exception;
}
