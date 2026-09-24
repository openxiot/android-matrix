package cc.openxiot.matrix.device.loach._sensor._co2._carbondioxidesensor.properties;

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
 * Property: 检测到CO2
 */
public class PropertyCarbonDioxideDetected extends PropertyController<Integer> {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:property:carbon-dioxide-detected:00000092:loach:co2:1";

    public PropertyCarbonDioxideDetected() {
        super(IID, new PropertyType(TYPE), new Access(false, true, true), DataFormat.UINT8);

        super.description().put("zh-CN", "检测到CO2");

        List<ValueDefinition<Integer>> valueList = new ArrayList<>();
        Map<String, String> _map0 = new HashMap<>();
        _map0.put("zh-CN", "CO2 Not Detected");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 0, _map0));

        Map<String, String> _map1 = new HashMap<>();
        _map1.put("zh-CN", "CO2 Detected");
        valueList.add(new ValueDefinition<>(DataFormat.UINT8, 1, _map1));

        super.constraintValue(new ValueList<>(valueList));

    }

}