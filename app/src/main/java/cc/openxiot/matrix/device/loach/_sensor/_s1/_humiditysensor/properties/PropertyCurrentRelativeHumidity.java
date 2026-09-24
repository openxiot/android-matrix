package cc.openxiot.matrix.device.loach._sensor._s1._humiditysensor.properties;

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
 * Property: 当前湿度
 */
public class PropertyCurrentRelativeHumidity extends PropertyController<Double> {

    public static final int IID = 13;
    public static final String TYPE = "urn:homekit-spec:property:current-relative-humidity:00000010:loach:s1:1";

    public PropertyCurrentRelativeHumidity() {
        super(IID, new PropertyType(TYPE), new Access(false, true, true), DataFormat.FLOAT);

        super.description().put("zh-CN", "当前湿度");

        super.constraintValue(new ValueRange<>(DataFormat.FLOAT, 0, 100, 1));

    }

}