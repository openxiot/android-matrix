package cc.openxiot.matrix.device.loach._sensor._s1._temperaturesensor.properties;

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
 * Property: 低电量
 */
public class PropertyStatusLowBattery extends PropertyController<Integer> {

    public static final int IID = 12;
    public static final String TYPE = "urn:homekit-spec:property:status-low-battery:00000079:loach:s1:1";

    public PropertyStatusLowBattery() {
        super(IID, new PropertyType(TYPE), new Access(false, true, true), DataFormat.UINT8);

        super.description().put("zh-CN", "低电量");

        List<ValueDefinition<Integer>> valueList = new ArrayList<>();
        Map<String, String> _map0 = new HashMap<>();
        _map0.put("uk-UA", "Battery Normal");
        _map0.put("en-US", "Battery Normal");
        _map0.put("lv-LV", "Battery Normal");
        _map0.put("ms-MY", "Battery Normal");
        _map0.put("ta-IN", "Battery Normal");
        _map0.put("zh-CN", "Battery Normal");
        _map0.put("nl-BE", "Battery Normal");
        _map0.put("kmr-IQ", "Battery Normal");
        _map0.put("ur-PK", "Battery Normal");
        _map0.put("el-GR", "Battery Normal");
        _map0.put("nl-NL", "Battery Normal");
        _map0.put("hi-IN", "Battery Normal");
        _map0.put("en-AU", "Battery Normal");
        _map0.put("hy-AM", "Battery Normal");
        _map0.put("he-IL", "Battery Normal");
        _map0.put("mk-MK", "Battery Normal");
        _map0.put("ja-JP", "Battery Normal");
        _map0.put("hu-HU", "Battery Normal");
        _map0.put("ml-IN", "Battery Normal");
        _map0.put("ne-NP", "Battery Normal");
        _map0.put("ka-GE", "Battery Normal");
        _map0.put("fr-CA", "Battery Normal");
        _map0.put("fa-IR", "Battery Normal");
        _map0.put("pl-PL", "Battery Normal");
        _map0.put("pt-PT", "Battery Normal");
        _map0.put("be-BY", "Battery Normal");
        _map0.put("ro-RO", "Battery Normal");
        _map0.put("fr-BE", "Battery Normal");
        _map0.put("ar-EG", "Battery Normal");
        _map0.put("ga-IE", "Battery Normal");
        _map0.put("et-EE", "Battery Normal");
        _map0.put("tr-TR", "Battery Normal");
        _map0.put("fr-FR", "Battery Normal");
        _map0.put("vi-VN", "Battery Normal");
        _map0.put("en-GB", "Battery Normal");
        _map0.put("km-KH", "Battery Normal");
        _map0.put("fi-FI", "Battery Normal");
        _map0.put("nb-NO", "Battery Normal");
        _map0.put("az-AZ", "Battery Normal");
        _map0.put("hr-HR", "Battery Normal");
        _map0.put("lt-LT", "Battery Normal");
        _map0.put("gl-ES", "Battery Normal");
        _map0.put("sl-SI", "Battery Normal");
        _map0.put("is-IS", "Battery Normal");
        _map0.put("bg-BG", "Battery Normal");
        _map0.put("kn-IN", "Battery Normal");
        _map0.put("cs-CZ", "Battery Normal");
        _map0.put("de-DE", "Battery Normal");
        _map0.put("ca-ES", "Battery Normal");
        _map0.put("zh-HK", "Battery Normal");
        _map0.put("zh-TW", "Battery Normal");
        _map0.put("ko-KR", "Battery Normal");
        _map0.put("pt-BR", "Battery Normal");
        _map0.put("sr-RS", "Battery Normal");
        _map0.put("sk-SK", "Battery Normal");
        _map0.put("es-ES", "Battery Normal");
        _map0.put("kk-KZ", "Battery Normal");
        _map0.put("it-IT", "Battery Normal");
        _map0.put("ru-RU", "Battery Normal");
        _map0.put("ku-IQ", "Battery Normal");
        _map0.put("bn-BD", "Battery Normal");
        _map0.put("sv-SE", "Battery Normal");
        _map0.put("id-ID", "Battery Normal");
        _map0.put("da-DK", "Battery Normal");
        _map0.put("mn-MN", "Battery Normal");
        _map0.put("th-TH", "Battery Normal");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 0, _map0));

        Map<String, String> _map1 = new HashMap<>();
        _map1.put("uk-UA", "Battery Low");
        _map1.put("en-US", "Battery Low");
        _map1.put("lv-LV", "Battery Low");
        _map1.put("ms-MY", "Battery Low");
        _map1.put("ta-IN", "Battery Low");
        _map1.put("zh-CN", "Battery Low");
        _map1.put("nl-BE", "Battery Low");
        _map1.put("kmr-IQ", "Battery Low");
        _map1.put("ur-PK", "Battery Low");
        _map1.put("el-GR", "Battery Low");
        _map1.put("nl-NL", "Battery Low");
        _map1.put("hi-IN", "Battery Low");
        _map1.put("en-AU", "Battery Low");
        _map1.put("hy-AM", "Battery Low");
        _map1.put("he-IL", "Battery Low");
        _map1.put("mk-MK", "Battery Low");
        _map1.put("ja-JP", "Battery Low");
        _map1.put("hu-HU", "Battery Low");
        _map1.put("ml-IN", "Battery Low");
        _map1.put("ne-NP", "Battery Low");
        _map1.put("ka-GE", "Battery Low");
        _map1.put("fr-CA", "Battery Low");
        _map1.put("fa-IR", "Battery Low");
        _map1.put("pl-PL", "Battery Low");
        _map1.put("pt-PT", "Battery Low");
        _map1.put("be-BY", "Battery Low");
        _map1.put("ro-RO", "Battery Low");
        _map1.put("fr-BE", "Battery Low");
        _map1.put("ar-EG", "Battery Low");
        _map1.put("ga-IE", "Battery Low");
        _map1.put("et-EE", "Battery Low");
        _map1.put("tr-TR", "Battery Low");
        _map1.put("fr-FR", "Battery Low");
        _map1.put("vi-VN", "Battery Low");
        _map1.put("en-GB", "Battery Low");
        _map1.put("km-KH", "Battery Low");
        _map1.put("fi-FI", "Battery Low");
        _map1.put("nb-NO", "Battery Low");
        _map1.put("az-AZ", "Battery Low");
        _map1.put("hr-HR", "Battery Low");
        _map1.put("lt-LT", "Battery Low");
        _map1.put("gl-ES", "Battery Low");
        _map1.put("sl-SI", "Battery Low");
        _map1.put("is-IS", "Battery Low");
        _map1.put("bg-BG", "Battery Low");
        _map1.put("kn-IN", "Battery Low");
        _map1.put("cs-CZ", "Battery Low");
        _map1.put("de-DE", "Battery Low");
        _map1.put("ca-ES", "Battery Low");
        _map1.put("zh-HK", "Battery Low");
        _map1.put("zh-TW", "Battery Low");
        _map1.put("ko-KR", "Battery Low");
        _map1.put("pt-BR", "Battery Low");
        _map1.put("sr-RS", "Battery Low");
        _map1.put("sk-SK", "Battery Low");
        _map1.put("es-ES", "Battery Low");
        _map1.put("kk-KZ", "Battery Low");
        _map1.put("it-IT", "Battery Low");
        _map1.put("ru-RU", "Battery Low");
        _map1.put("ku-IQ", "Battery Low");
        _map1.put("bn-BD", "Battery Low");
        _map1.put("sv-SE", "Battery Low");
        _map1.put("id-ID", "Battery Low");
        _map1.put("da-DK", "Battery Low");
        _map1.put("mn-MN", "Battery Low");
        _map1.put("th-TH", "Battery Low");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 1, _map1));

        super.constraintValue(new ValueList<>(valueList));

    }

}