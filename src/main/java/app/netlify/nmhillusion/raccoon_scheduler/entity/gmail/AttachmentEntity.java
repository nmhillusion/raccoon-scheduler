package app.netlify.nmhillusion.raccoon_scheduler.entity.gmail;

import org.json.JSONObject;
import tech.nmhillusion.n2mix.type.Stringeable;

/**
 * date: 2022-11-26
 * <p>
 * created-by: nmhillusion
 */

public class AttachmentEntity extends Stringeable {
    private String name;
    private String contentType;
    private String base64Data;

    public String getName() {
        return name;
    }

    public AttachmentEntity setName(String name) {
        this.name = name;
        return this;
    }

    public String getContentType() {
        return contentType;
    }

    public AttachmentEntity setContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public String getBase64Data() {
        return base64Data;
    }

    public AttachmentEntity setBase64Data(String base64Data) {
        this.base64Data = base64Data;
        return this;
    }

    public JSONObject toJson() {
        final JSONObject jsonObject = new JSONObject();

        jsonObject.put("name", getName());
        jsonObject.put("contentType", getContentType());
        jsonObject.put("base64Data", getBase64Data());

        return jsonObject;
    }
}
