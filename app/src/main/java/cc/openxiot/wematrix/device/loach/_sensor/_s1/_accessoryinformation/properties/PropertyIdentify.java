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
 * Property: 识别
 */
public class PropertyIdentify extends PropertyController<Boolean> {

    public static final int IID = 3;
    public static final String TYPE = "urn:homekit-spec:property:identify:00000014:loach:s1:1";

    public PropertyIdentify() {
        super(IID, new PropertyType(TYPE), new Access(true, false, false), DataFormat.BOOL);

        super.description().put("zh-CN", "识别");


    }

}