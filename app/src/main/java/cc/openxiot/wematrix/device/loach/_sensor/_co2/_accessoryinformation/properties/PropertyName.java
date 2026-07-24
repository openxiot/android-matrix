package cc.openxiot.wematrix.device.loach._sensor._co2._accessoryinformation.properties;

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
 * Property: 名称
 */
public class PropertyName extends PropertyController<String> {

    public static final int IID = 6;
    public static final String TYPE = "urn:homekit-spec:property:name:00000023:loach:co2:1";

    public PropertyName() {
        super(IID, new PropertyType(TYPE), new Access(false, true, false), DataFormat.STRING);

        super.description().put("zh-CN", "名称");


    }

}