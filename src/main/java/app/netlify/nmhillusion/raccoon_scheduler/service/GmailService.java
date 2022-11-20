package app.netlify.nmhillusion.raccoon_scheduler.service;

/**
 * date: 2022-11-20
 * <p>
 * created-by: nmhillusion
 */
public interface GmailService {
    void sendMail(String recipient, String subject, String body) throws Exception;
}
