package link.locutus.util;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class TrounceUtil {
    public static String getAlert(Document dom) {
        // get title
        String title = dom.title();
        StringBuilder response = new StringBuilder();
        response.append("Title: ").append(title).append("\n");
        // get alerts with class: text-red-600
        for (Element elementsByClass : dom.getElementsByClass("text-red-600")) {
            response.append(elementsByClass.text()).append("\n");
        }
        return response.toString();

    }
}
