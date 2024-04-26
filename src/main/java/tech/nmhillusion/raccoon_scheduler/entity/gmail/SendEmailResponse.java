package tech.nmhillusion.raccoon_scheduler.entity.gmail;

import org.json.JSONObject;
import tech.nmhillusion.n2mix.type.Stringeable;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */

public class SendEmailResponse extends Stringeable {
    private boolean success;
    private JSONObject data;
    private String message;

    public static SendEmailResponse fromJSON(JSONObject jsonObject) {
        return new SendEmailResponse()
                .setSuccess(jsonObject.optBoolean("success"))
                .setMessage(jsonObject.optString("message"))
                .setData(jsonObject.optJSONObject("data"))
                ;
    }

    public boolean getSuccess() {
        return success;
    }

    public SendEmailResponse setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public JSONObject getData() {
        return data;
    }

    public SendEmailResponse setData(JSONObject data) {
        this.data = data;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public SendEmailResponse setMessage(String message) {
        this.message = message;
        return this;
    }
}
