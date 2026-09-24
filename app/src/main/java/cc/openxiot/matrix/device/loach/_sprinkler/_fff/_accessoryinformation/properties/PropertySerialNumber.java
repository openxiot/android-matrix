package cc.openxiot.matrix.device.loach._sprinkler._fff._accessoryinformation.properties;

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
 * Property: 序列号
 */
public class PropertySerialNumber extends PropertyController<String> {

    public static final int IID = 7;
    public static final String TYPE = "urn:homekit-spec:property:serial-number:00000030:loach:fff:1";

    public PropertySerialNumber() {
        super(IID, new PropertyType(TYPE), new Access(false, true, false), DataFormat.STRING);

        super.description().put("zh-CN", "序列号");


    }

}