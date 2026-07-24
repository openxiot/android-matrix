package cc.openxiot.wematrix.device.loach._sensor._s1._accessoryinformation.properties;

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
 * Property: 制造商
 */
public class PropertyManufacturer extends PropertyController<String> {

    public static final int IID = 4;
    public static final String TYPE = "urn:homekit-spec:property:manufacturer:00000020:loach:s1:1";

    public PropertyManufacturer() {
        super(IID, new PropertyType(TYPE), new Access(false, true, false), DataFormat.STRING);

        super.description().put("zh-CN", "制造商");


    }

}