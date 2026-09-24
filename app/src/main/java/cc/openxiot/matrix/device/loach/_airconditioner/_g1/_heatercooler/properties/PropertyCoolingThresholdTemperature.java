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
 * Property: 制冷阈值
 */
public class PropertyCoolingThresholdTemperature extends PropertyController<Double> {

    public static final int IID = 15;
    public static final String TYPE = "urn:homekit-spec:property:cooling-threshold-temperature:0000000d:loach:g1:1";

    public PropertyCoolingThresholdTemperature() {
        super(IID, new PropertyType(TYPE), new Access(true, true, true), DataFormat.FLOAT);

        super.description().put("zh-CN", "制冷阈值");

        super.constraintValue(new ValueRange<>(DataFormat.FLOAT, 16, 32, 0.5));

    }

}