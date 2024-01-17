package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.raccoon_scheduler.entity.gmail.MailEntity;
import app.netlify.nmhillusion.raccoon_scheduler.service.AdminService;
import app.netlify.nmhillusion.raccoon_scheduler.service.GmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static tech.nmhillusion.n2mix.helper.log.LogHelper.getLogger;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */

@Service
public class AdminServiceImpl implements AdminService {
    @Autowired
    private GmailService gmailService;

    @Override
    public void reportError(String title, Throwable ex, Object... additionalInfo) {
        try {
            gmailService.sendMail(
                    new MailEntity()
                            .setSubject("[RaccoonScheduler:ReportError] " + title)
                            .setRecipientMails(List.of("nguyenminhhieu.geek@gmail.com"))
                            .setHtmlBody("An error occur: " + ex.getMessage() +
                                    "\r\n Exception Stack:" + getContentOfException(ex) +
                                    "\r\n Additional Info: " + Arrays.toString(additionalInfo))
            );
        } catch (Exception ex_) {
            getLogger(this).error(ex_);
        }
    }

    private String getContentOfException(Throwable ex) {
        final StringBuilder throwableStringBuilder = new StringBuilder();

        do {
            throwableStringBuilder.append(ex.getStackTrace()[0].toString()).append("\n");
            ex = ex.getCause();
        } while (null != ex);

        return throwableStringBuilder.toString();
    }

    @Override
    public String currentStatus(String username) {
        return "[on " + username + "] all things OK";
    }
}
