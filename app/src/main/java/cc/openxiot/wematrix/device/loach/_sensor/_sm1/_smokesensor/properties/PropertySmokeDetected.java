package cc.openxiot.wematrix.device.loach._sensor._sm1._smokesensor.properties;

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
 * Property: 检测到烟雾
 */
public class PropertySmokeDetected extends PropertyController<Integer> {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:property:smoke-detected:00000076:loach:sm1:1";

    public PropertySmokeDetected() {
        super(IID, new PropertyType(TYPE), new Access(false, true, true), DataFormat.UINT8);

        super.description().put("zh-CN", "检测到烟雾");

        List<ValueDefinition<Integer>> valueList = new ArrayList<>();
        Map<String, String> _map0 = new HashMap<>();
        _map0.put("uk-UA", "ДИМ НЕ ВИЯВЛЕНО");
        _map0.put("en-US", "Smoke Not Detected");
        _map0.put("lv-LV", "DŪMI NAV ATKLĀTI");
        _map0.put("ms-MY", "Smoke Not Detected");
        _map0.put("ta-IN", "புகை கண்டறியப்படவில்லை");
        _map0.put("zh-CN", "Smoke Not Detected");
        _map0.put("nl-BE", "Smoke Not Detected");
        _map0.put("kmr-IQ", "SMOKE_NOT_DETECTED");
        _map0.put("ur-PK", "دھوئیں کا پتہ نہیں چلا");
        _map0.put("el-GR", "Smoke Not Detected");
        _map0.put("nl-NL", "Smoke Not Detected");
        _map0.put("hi-IN", "धुएँ का पता नहीं चला");
        _map0.put("en-AU", "Smoke Not Detected");
        _map0.put("hy-AM", "ՀԱՅՏՆԱԲԵՐՎԵԼ Է SMOKE_NOT_DETECTED");
        _map0.put("he-IL", "עשן לא זוהה");
        _map0.put("mk-MK", "НЕ Е ОТКРИЕН ЧАД");
        _map0.put("ja-JP", "Smoke Not Detected");
        _map0.put("hu-HU", "FÜST NEM ÉRZÉKELVE");
        _map0.put("ml-IN", "പുക കണ്ടെത്തിയില്ല");
        _map0.put("ne-NP", "धुवाँ पत्ता लागेन");
        _map0.put("ka-GE", "SMOKE_NOT_DETECTED");
        _map0.put("fr-CA", "Smoke Not Detected");
        _map0.put("fa-IR", "دود تشخیص داده نشد");
        _map0.put("pl-PL", "NIE_WYKRYTO_DYM");
        _map0.put("pt-PT", "Smoke Not Detected");
        _map0.put("be-BY", "ДЫМ НЕ ВЫЯЎЛЕНЫ");
        _map0.put("ro-RO", "FUM NEDETECTAT");
        _map0.put("fr-BE", "Smoke Not Detected");
        _map0.put("ar-EG", "لم يتم اكتشاف دخان");
        _map0.put("ga-IE", "NÁ AITHEASC ÁBHÁIGH");
        _map0.put("et-EE", "SUITSU EI TUVASTATUD");
        _map0.put("tr-TR", "Duman Algılanmadı");
        _map0.put("fr-FR", "Smoke Not Detected");
        _map0.put("vi-VN", "Smoke Not Detected");
        _map0.put("en-GB", "Smoke Not Detected");
        _map0.put("km-KH", "Smoke Not Detected");
        _map0.put("fi-FI", "SAVUA EI HAVAITTU");
        _map0.put("nb-NO", "RØYK IKKE OPPDAGET");
        _map0.put("az-AZ", "TÜSTÜ_AŞKAR EDİLMƏYİB");
        _map0.put("hr-HR", "DIM NIJE OTKRIVEN");
        _map0.put("lt-LT", "DŪMAI NEAPTIKTI");
        _map0.put("gl-ES", "SMOKE_NOT_DETECTED");
        _map0.put("sl-SI", "DIM NI ZAZNAN");
        _map0.put("is-IS", "REYKUR EKKI SKYNDUR");
        _map0.put("bg-BG", "НЕ Е ОТКРИТ ДИМ");
        _map0.put("kn-IN", "ಹೊಗೆ ಪತ್ತೆಯಾಗಿಲ್ಲ");
        _map0.put("cs-CZ", "NEDETEKTÓVÁNY");
        _map0.put("de-DE", "Smoke Not Detected");
        _map0.put("ca-ES", "NO S'HA DETECTAT FUM");
        _map0.put("zh-HK", "Smoke Not Detected");
        _map0.put("zh-TW", "Smoke Not Detected");
        _map0.put("ko-KR", "Smoke Not Detected");
        _map0.put("pt-BR", "Smoke Not Detected");
        _map0.put("sr-RS", "ДИМ НИЈЕ ОТКРИВЕН");
        _map0.put("sk-SK", "NEDETTOXOVANÝ DUH");
        _map0.put("es-ES", "Smoke Not Detected");
        _map0.put("kk-KZ", "SMOKE_NOT_DETECTED");
        _map0.put("it-IT", "Smoke Not Detected");
        _map0.put("ru-RU", "Smoke Not Detected");
        _map0.put("ku-IQ", "دووکەڵ_نەدۆزرایەوە");
        _map0.put("bn-BD", "ধোঁয়া সনাক্ত হয়নি");
        _map0.put("sv-SE", "RÖK EJ UPPTÄCKT");
        _map0.put("id-ID", "Smoke Not Detected");
        _map0.put("da-DK", "RØG IKKE REGISTRERET");
        _map0.put("mn-MN", "SMOKE_NOT_DETECTED");
        _map0.put("th-TH", "Smoke Not Detected");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 0, _map0));

        Map<String, String> _map1 = new HashMap<>();
        _map1.put("uk-UA", "Виявлено дим");
        _map1.put("en-US", "Smoke Detected");
        _map1.put("lv-LV", "Atklāti dūmi");
        _map1.put("ms-MY", "Asap Dikesan");
        _map1.put("ta-IN", "புகை கண்டறியப்பட்டது");
        _map1.put("zh-CN", "Smoke Detected");
        _map1.put("nl-BE", "Smoke Detected");
        _map1.put("kmr-IQ", "Dûman Hate Dîtin");
        _map1.put("ur-PK", "دھوئیں کا پتہ چلا");
        _map1.put("el-GR", "Smoke Detected");
        _map1.put("nl-NL", "Smoke Detected");
        _map1.put("hi-IN", "धुएँ का पता चला");
        _map1.put("en-AU", "Smoke Detected");
        _map1.put("hy-AM", "Հայտնաբերվել է ծուխ");
        _map1.put("he-IL", "עשן זוהה");
        _map1.put("mk-MK", "Откриен чад");
        _map1.put("ja-JP", "Smoke Detected");
        _map1.put("hu-HU", "Füst érzékelve");
        _map1.put("ml-IN", "പുക കണ്ടെത്തി");
        _map1.put("ne-NP", "धुवाँ पत्ता लाग्यो");
        _map1.put("ka-GE", "კვამლი აღმოჩენილია");
        _map1.put("fr-CA", "Smoke Detected");
        _map1.put("fa-IR", "دود شناسایی شد");
        _map1.put("pl-PL", "Wykryto Dym");
        _map1.put("pt-PT", "Smoke Detected");
        _map1.put("be-BY", "Выяўлены дым");
        _map1.put("ro-RO", "Fum detectat");
        _map1.put("fr-BE", "Smoke Detected");
        _map1.put("ar-EG", "تم اكتشاف دخان");
        _map1.put("ga-IE", "Deatach Braite");
        _map1.put("et-EE", "Suits tuvastatud");
        _map1.put("tr-TR", "Duman Algılandı");
        _map1.put("fr-FR", "Smoke Detected");
        _map1.put("vi-VN", "Phát hiện Khói");
        _map1.put("en-GB", "Smoke Detected");
        _map1.put("km-KH", "រកឃើញផ្សែង");
        _map1.put("fi-FI", "Savu havaittu");
        _map1.put("nb-NO", "Røyk oppdaget");
        _map1.put("az-AZ", "Tüstü aşkarlandı");
        _map1.put("hr-HR", "Otkriven dim");
        _map1.put("lt-LT", "Aptikti dūmai");
        _map1.put("gl-ES", "Fume Detectado");
        _map1.put("sl-SI", "Zaznan dim");
        _map1.put("is-IS", "Reykur greindur");
        _map1.put("bg-BG", "Открит дим");
        _map1.put("kn-IN", "ಹೊಗೆ ಪತ್ತೆಯಾಗಿದೆ");
        _map1.put("cs-CZ", "Detekován Kouř");
        _map1.put("de-DE", "Smoke Detected");
        _map1.put("ca-ES", "Fum Detectat");
        _map1.put("zh-HK", "Smoke Detected");
        _map1.put("zh-TW", "Smoke Detected");
        _map1.put("ko-KR", "Smoke Detected");
        _map1.put("pt-BR", "Smoke Detected");
        _map1.put("sr-RS", "Откривен дим");
        _map1.put("sk-SK", "Detekovaný Dym");
        _map1.put("es-ES", "Smoke Detected");
        _map1.put("kk-KZ", "Түтін Анықталды");
        _map1.put("it-IT", "Smoke Detected");
        _map1.put("ru-RU", "Smoke Detected");
        _map1.put("ku-IQ", "دووکەڵ دۆزرایەوە");
        _map1.put("bn-BD", "ধোঁয়া সনাক্ত হয়েছে");
        _map1.put("sv-SE", "Rök upptäckt");
        _map1.put("id-ID", "Asap Terdeteksi");
        _map1.put("da-DK", "Røg registreret");
        _map1.put("mn-MN", "Утаа Илрүүлсэн");
        _map1.put("th-TH", "ตรวจพบควัน");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 1, _map1));

        super.constraintValue(new ValueList<>(valueList));

    }

}