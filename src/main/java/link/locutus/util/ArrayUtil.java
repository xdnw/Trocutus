package link.locutus.util;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ArrayUtil {
    public static <K, T extends Number> Map<K, T> add(Map<K, T> a, Map<K, T> b) {
        if (a.isEmpty()) {
            return b;
        } else if (b.isEmpty()) {
            return a;
        }
        LinkedHashMap<K, T> copy = new LinkedHashMap<>();
        Set<K> keys = new HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        for (K type : keys) {
            Number v1 = a.get(type);
            Number v2 = b.get(type);
            Number total = v1 == null ? v2 : (v2 == null ? v1 : MathMan.add(v1, v2));
            if (total != null && total.doubleValue() != 0) {
                copy.put(type, (T) total);
            }
        }
        return copy;
    }
}
