package cc.openxiot.wematrix.device.loach._sensor._l1._accessoryinformation.properties;

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
 * Property: 硬件版本
 */
public class PropertyHardwareRevision extends PropertyController<String> {

    public static final int IID = 9;
    public static final String TYPE = "urn:homekit-spec:property:hardware-revision:00000053:loach:l1:1";

    public PropertyHardwareRevision() {
        super(IID, new PropertyType(TYPE), new Access(false, true, false), DataFormat.STRING);

        super.description().put("zh-CN", "硬件版本");


    }

}