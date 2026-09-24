package cc.openxiot.matrix.device.loach._airconditioner._g1._heatercooler.properties;

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

    public static final int IID = 14;
    public static final String TYPE = "urn:homekit-spec:property:current-temperature:00000011:loach:g1:1";

    public PropertyCurrentTemperature() {
        super(IID, new PropertyType(TYPE), new Access(false, true, true), DataFormat.FLOAT);

        super.description().put("zh-CN", "当前温度");

        super.constraintValue(new ValueRange<>(DataFormat.FLOAT, 0, 100, 0.5));

    }

}