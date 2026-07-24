package cc.openxiot.wematrix.device.loach._airconditioner._g1._heatercooler.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import cn.geekcity.xiot.spec.definition.property.*;
import cn.geekcity.xiot.spec.definition.property.data.DataFormat;
import cn.geekcity.xiot.spec.definition.urn.PropertyType;
import cn.geekcity.xiot.spec.error.IotError;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.PropertyController;



/**
 * Property: 活动状态
 */
public class PropertyActive extends PropertyController<Integer> {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:property:active:000000b0:loach:g1:1";

    public PropertyActive() {
        super(IID, new PropertyType(TYPE), new Access(true, true, true), DataFormat.UINT8);

        super.description().put("zh-CN", "活动状态");

        List<ValueDefinition<Integer>> valueList = new ArrayList<>();
        Map<String, String> _map0 = new HashMap<>();
        _map0.put("uk-UA", "НЕАКТИВНИЙ");
        _map0.put("en-US", "Inactive");
        _map0.put("lv-LV", "NEAKTĪVS");
        _map0.put("ms-MY", "Inactive");
        _map0.put("ta-IN", "செயலற்று");
        _map0.put("zh-CN", "Inactive");
        _map0.put("nl-BE", "Inactive");
        _map0.put("kmr-IQ", "INACTIVE");
        _map0.put("ur-PK", "غیر فعال");
        _map0.put("el-GR", "Inactive");
        _map0.put("nl-NL", "Inactive");
        _map0.put("hi-IN", "निष्क्रिय");
        _map0.put("en-AU", "Inactive");
        _map0.put("hy-AM", "Օտարված գույքը");
        _map0.put("he-IL", "לא פעיל");
        _map0.put("mk-MK", "НЕАКТИВЕН");
        _map0.put("ja-JP", "Inactive");
        _map0.put("hu-HU", "INAKTÍV");
        _map0.put("ml-IN", "നിഷ്ക്രിയം");
        _map0.put("ne-NP", "निष्क्रिय");
        _map0.put("ka-GE", "არააქტიური");
        _map0.put("fr-CA", "Inactive");
        _map0.put("fa-IR", "غیرفعال");
        _map0.put("pl-PL", "Nieaktywny");
        _map0.put("pt-PT", "Inactive");
        _map0.put("be-BY", "НЕАКТЫЎНЫ");
        _map0.put("ro-RO", "INACTIV");
        _map0.put("fr-BE", "Inactive");
        _map0.put("ar-EG", "غير نشط");
        _map0.put("ga-IE", "Ná Léi");
        _map0.put("et-EE", "PASSIIVNE");
        _map0.put("tr-TR", "Pasif");
        _map0.put("fr-FR", "Inactive");
        _map0.put("vi-VN", "Inactive");
        _map0.put("en-GB", "Inactive");
        _map0.put("km-KH", "Inactive");
        _map0.put("fi-FI", "PASSIIVINEN");
        _map0.put("nb-NO", "INAKTIV");
        _map0.put("az-AZ", "Fəal deyil");
        _map0.put("hr-HR", "NEAKTIVAN");
        _map0.put("lt-LT", "NEaktyvus");
        _map0.put("gl-ES", "INACTIVO");
        _map0.put("sl-SI", "NEAKTIVNO");
        _map0.put("is-IS", "ÓVIRKUR");
        _map0.put("bg-BG", "НЕАКТИВЕН");
        _map0.put("kn-IN", "ನಿಷ್ಕ್ರಿಯ");
        _map0.put("cs-CZ", "Neaktivní");
        _map0.put("de-DE", "Inactive");
        _map0.put("ca-ES", "INACTIU");
        _map0.put("zh-HK", "Inactive");
        _map0.put("zh-TW", "Inactive");
        _map0.put("ko-KR", "Inactive");
        _map0.put("pt-BR", "Inactive");
        _map0.put("sr-RS", "НЕАКТИВАН");
        _map0.put("sk-SK", "Neaktívne");
        _map0.put("es-ES", "Inactive");
        _map0.put("kk-KZ", "INACTIVE");
        _map0.put("it-IT", "Inactive");
        _map0.put("ru-RU", "Inactive");
        _map0.put("ku-IQ", "ناچالاک");
        _map0.put("bn-BD", "নিষ্ক্রিয়");
        _map0.put("sv-SE", "INAKTIV");
        _map0.put("id-ID", "Inactive");
        _map0.put("da-DK", "INAKTIV");
        _map0.put("mn-MN", "INACTIVE");
        _map0.put("th-TH", "Inactive");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 0, _map0));

        Map<String, String> _map1 = new HashMap<>();
        _map1.put("uk-UA", "Активний");
        _map1.put("en-US", "Active");
        _map1.put("lv-LV", "Aktīvs");
        _map1.put("ms-MY", "Aktif");
        _map1.put("ta-IN", "செயலில்");
        _map1.put("zh-CN", "Active");
        _map1.put("nl-BE", "Active");
        _map1.put("kmr-IQ", "Çalak");
        _map1.put("ur-PK", "فعال");
        _map1.put("el-GR", "Active");
        _map1.put("nl-NL", "Active");
        _map1.put("hi-IN", "सक्रिय");
        _map1.put("en-AU", "Active");
        _map1.put("hy-AM", "Առկա գույքը");
        _map1.put("he-IL", "פעיל");
        _map1.put("mk-MK", "Активен");
        _map1.put("ja-JP", "Active");
        _map1.put("hu-HU", "Aktív");
        _map1.put("ml-IN", "സജീവം");
        _map1.put("ne-NP", "सक्रिय");
        _map1.put("ka-GE", "აქტიური");
        _map1.put("fr-CA", "Active");
        _map1.put("fa-IR", "فعال");
        _map1.put("pl-PL", "Aktywny");
        _map1.put("pt-PT", "Active");
        _map1.put("be-BY", "Актыўны");
        _map1.put("ro-RO", "Activ");
        _map1.put("fr-BE", "Active");
        _map1.put("ar-EG", "نشط");
        _map1.put("ga-IE", "Gníomhach");
        _map1.put("et-EE", "Aktiivne");
        _map1.put("tr-TR", "Aktif");
        _map1.put("fr-FR", "Active");
        _map1.put("vi-VN", "Hoạt động");
        _map1.put("en-GB", "Active");
        _map1.put("km-KH", "សកម្ម");
        _map1.put("fi-FI", "Aktiivinen");
        _map1.put("nb-NO", "Aktiv");
        _map1.put("az-AZ", "Fəaldır");
        _map1.put("hr-HR", "Aktivan");
        _map1.put("lt-LT", "Aktyvus");
        _map1.put("gl-ES", "Activo");
        _map1.put("sl-SI", "Aktivno");
        _map1.put("is-IS", "Virkur");
        _map1.put("bg-BG", "Активен");
        _map1.put("kn-IN", "ಸಕ್ರಿಯ");
        _map1.put("cs-CZ", "Aktivní");
        _map1.put("de-DE", "Active");
        _map1.put("ca-ES", "Actiu");
        _map1.put("zh-HK", "Active");
        _map1.put("zh-TW", "Active");
        _map1.put("ko-KR", "Active");
        _map1.put("pt-BR", "Active");
        _map1.put("sr-RS", "Активан");
        _map1.put("sk-SK", "Aktívny");
        _map1.put("es-ES", "Active");
        _map1.put("kk-KZ", "Белсенді");
        _map1.put("it-IT", "Active");
        _map1.put("ru-RU", "Active");
        _map1.put("ku-IQ", "چالاک");
        _map1.put("bn-BD", "সক্রিয়");
        _map1.put("sv-SE", "Aktiv");
        _map1.put("id-ID", "Aktif");
        _map1.put("da-DK", "Aktiv");
        _map1.put("mn-MN", "Идэвхтэй");
        _map1.put("th-TH", "เปิดใช้งาน");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 1, _map1));

        super.constraintValue(new ValueList<>(valueList));

    }

}