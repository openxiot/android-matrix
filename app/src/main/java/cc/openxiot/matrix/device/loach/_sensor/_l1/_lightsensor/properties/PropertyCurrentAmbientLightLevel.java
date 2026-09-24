package cc.openxiot.matrix.device.loach._sensor._l1._lightsensor.properties;

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
 * Property: 光照强度
 */
public class PropertyCurrentAmbientLightLevel extends PropertyController<Double> {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:property:current-ambient-light-level:0000006b:loach:l1:1";

    public PropertyCurrentAmbientLightLevel() {
        super(IID, new PropertyType(TYPE), new Access(false, true, true), DataFormat.FLOAT);

        super.description().put("zh-CN", "光照强度");

        super.constraintValue(new ValueRange<>(DataFormat.FLOAT, 0.0001, 100000, 0.0001));

    }

}