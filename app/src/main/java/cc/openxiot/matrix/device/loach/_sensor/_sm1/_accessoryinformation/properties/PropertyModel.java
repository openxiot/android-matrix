package cc.openxiot.matrix.device.loach._sensor._sm1._accessoryinformation.properties;

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
 * Property: 型号
 */
public class PropertyModel extends PropertyController<String> {

    public static final int IID = 5;
    public static final String TYPE = "urn:homekit-spec:property:model:00000021:loach:sm1:1";

    public PropertyModel() {
        super(IID, new PropertyType(TYPE), new Access(false, true, false), DataFormat.STRING);

        super.description().put("zh-CN", "型号");


    }

}