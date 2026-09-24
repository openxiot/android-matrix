package cc.openxiot.matrix.device.loach._sprinkler._fff._irrigationsystem.properties;

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
 * Property: 程序模式
 */
public class PropertyProgramMode extends PropertyController<Integer> {

    public static final int IID = 12;
    public static final String TYPE = "urn:homekit-spec:property:program-mode:000000d1:loach:fff:1";

    public PropertyProgramMode() {
        super(IID, new PropertyType(TYPE), new Access(false, true, true), DataFormat.UINT8);

        super.description().put("zh-CN", "程序模式");

        List<ValueDefinition<Integer>> valueList = new ArrayList<>();
        Map<String, String> _map0 = new HashMap<>();
        _map0.put("zh-CN", "No Program");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 0, _map0));

        Map<String, String> _map1 = new HashMap<>();
        _map1.put("zh-CN", "Program Scheduled");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 1, _map1));

        Map<String, String> _map2 = new HashMap<>();
        _map2.put("zh-CN", "Manual");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 2, _map2));

        super.constraintValue(new ValueList<>(valueList));

    }

}