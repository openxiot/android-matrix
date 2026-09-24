package cc.openxiot.matrix.device.loach._switch._k3._switch13.properties;

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
 * Property: 开关
 */
public class PropertyOn extends PropertyController<Boolean> {

    public static final int IID = 1;
    public static final String TYPE = "urn:homekit-spec:property:on:00000025:loach:k3:1";

    public PropertyOn() {
        super(IID, new PropertyType(TYPE), new Access(true, true, true), DataFormat.BOOL);

        super.description().put("zh-CN", "开关");


    }

}