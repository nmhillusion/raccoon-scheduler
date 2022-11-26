package app.netlify.nmhillusion.raccoon_scheduler.config;

import app.netlify.nmhillusion.n2mix.helper.YamlReader;
import app.netlify.nmhillusion.n2mix.helper.log.LogHelper;
import app.netlify.nmhillusion.n2mix.type.ChainMap;

import java.io.IOException;
import java.io.InputStream;

/**
 * date: 2022-11-26
 * <p>
 * created-by: nmhillusion
 */

public class GmailConstant {
    private static final GmailConstant instance;

    static {
        try {
            instance = new GmailConstant();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public final String ENDPOINT_URL;
    public final String METHOD__SEND_MAIL;

    private GmailConstant() throws IOException {
        ENDPOINT_URL = getConfig("config.endpointUrl");
        METHOD__SEND_MAIL = getConfig("method.sendMail");

        LogHelper.getLog(this).infoFormat("$ENDPOINT_URL, $METHOD__SEND_MAIL", new ChainMap<String, String>()
                .chainPut("ENDPOINT_URL", ENDPOINT_URL)
                .chainPut("METHOD__SEND_MAIL", METHOD__SEND_MAIL)
        );
    }

    public static GmailConstant getInstance() {
        return instance;
    }

    private String getConfig(String key) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("app-config/gmail.yml")) {
            return new YamlReader(is).getProperty(key, String.class, "");
        }
    }


}
