package app.netlify.nmhillusion.raccoon_scheduler.entity.gmail;

import app.netlify.nmhillusion.n2mix.type.Stringeable;

import java.util.ArrayList;
import java.util.List;

/**
 * date: 2022-11-26
 * <p>
 * created-by: nmhillusion
 */

public class MailEntity extends Stringeable {
    private List<String> recipientMails;
    private String subject;
    private List<String> ccMails;
    private List<String> bccMails;
    private String htmlBody;
    private List<AttachmentEntity> attachments;


    public List<String> getRecipientMails() {
        return recipientMails;
    }

    public MailEntity setRecipientMails(List<String> recipientMails) {
        this.recipientMails = recipientMails;
        return this;
    }

    public String getSubject() {
        return subject;
    }

    public MailEntity setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public List<String> getCcMails() {
        return null == ccMails ? new ArrayList<>() : ccMails;
    }

    public MailEntity setCcMails(List<String> ccMails) {
        this.ccMails = ccMails;
        return this;
    }

    public List<String> getBccMails() {
        return null == bccMails ? new ArrayList<>() : bccMails;
    }

    public MailEntity setBccMails(List<String> bccMails) {
        this.bccMails = bccMails;
        return this;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public MailEntity setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
        return this;
    }

    public List<AttachmentEntity> getAttachments() {
        return null != attachments ? attachments : new ArrayList<>();
    }

    public MailEntity setAttachments(List<AttachmentEntity> attachments) {
        this.attachments = attachments;
        return this;
    }
}
