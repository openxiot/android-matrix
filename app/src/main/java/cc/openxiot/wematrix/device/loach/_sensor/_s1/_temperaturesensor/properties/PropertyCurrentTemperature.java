package cc.openxiot.wematrix.device.loach._sensor._s1._temperaturesensor.properties;

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
 * Property: 当前温度
 */
public class PropertyCurrentTemperature extends PropertyController<Double> {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:property:current-temperature:00000011:loach:s1:1";

    public PropertyCurrentTemperature() {
        super(IID, new PropertyType(TYPE), new Access(false, true, true), DataFormat.FLOAT);

        super.description().put("zh-CN", "当前温度");

        super.constraintValue(new ValueRange<>(DataFormat.FLOAT, -50, 100, 0.1));

    }

}