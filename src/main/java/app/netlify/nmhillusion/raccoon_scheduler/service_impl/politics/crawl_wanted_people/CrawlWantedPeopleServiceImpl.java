package app.netlify.nmhillusion.raccoon_scheduler.service_impl.politics.crawl_wanted_people;

import app.netlify.nmhillusion.n2mix.helper.YamlReader;
import app.netlify.nmhillusion.n2mix.helper.firebase.FirebaseWrapper;
import app.netlify.nmhillusion.n2mix.helper.http.HttpHelper;
import app.netlify.nmhillusion.n2mix.util.CollectionUtil;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics.politics_rulers.PendingUserEntity;
import app.netlify.nmhillusion.raccoon_scheduler.service.GmailService;
import app.netlify.nmhillusion.raccoon_scheduler.service.politics.CrawlWantedPeopleService;
import app.netlify.nmhillusion.raccoon_scheduler.service_impl.BaseSchedulerServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLog;

/**
 * date: 2023-02-12
 * <p>
 * created-by: nmhillusion
 */
@Service
public class CrawlWantedPeopleServiceImpl extends BaseSchedulerServiceImpl implements CrawlWantedPeopleService {

    private static final String MAIN_RULERS_PAGE_URL = "http://vpcqcsdt.bocongan.gov.vn/Truy-n%C3%A3-TP/%C4%90%E1%BB%91i-t%C6%B0%E1%BB%A3ng-truy-n%C3%A3";
    private static final int MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS = 10_000;
    private static final Charset RULERS_CHARSET = StandardCharsets.UTF_8;

    private final HttpHelper httpHelper = new HttpHelper();

    private final FirebaseWrapper firebaseWrapper = FirebaseWrapper.getInstance();

    @Autowired
    private GmailService gmailService;
    private YamlReader yamlReader;
    @Value("${service.politics.crawl-wanted-people.enable}")
    private boolean enableExecution;

    @Value("${service.politics.crawl-wanted-people.testing}")
    private boolean isTesting;
    private DateTimeFormatter exportDataDateTimeFormatter;

    private synchronized String getConfig(String key) {
        try {
            if (null == yamlReader) {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("service-config/politics/wanted-people.yml")) {
                    yamlReader = new YamlReader(is);
                }
            }

            return yamlReader.getProperty(key, String.class, null);
        } catch (Exception ex) {
            getLog(this).error(ex);
            return "";
        }
    }

    @PostConstruct
    private void init() {
        this.exportDataDateTimeFormatter = new DateTimeFormatterBuilder()
                .appendPattern(getConfig("export.excel.date-format"))
                .toFormatter();
    }

    private String buildMailFromTemplate(PendingUserEntity pendingUser) {
        try {
            final String mailTemplate = getConfig("mail.template");

            return mailTemplate.replace("{{user_fullName}}", pendingUser.getFullName());
        } catch (Exception ex) {
            getLog(this).error(ex);
            return "";
        }
    }

    @Override
    public boolean isEnableExecution() {
        return enableExecution;
    }

    @Override
    public void doExecute() throws Exception {
        if (CollectionUtil.isNullOrEmpty(getPendingUsers())) {
            getLog(this).warn("Do not run because of empty pending users");
            return;
        }

        getLog(this).info("Running for fetching wanted people");
    }

    private List<?> getPendingUsers() {
        return new ArrayList<>();
    }

}
