package cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation.properties;

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
 * Property: 附件标志
 */
public class PropertyAccessoryFlags extends PropertyController<Long> {

    public static final int IID = 8;
    public static final String TYPE = "urn:homekit-spec:property:accessory-flags:000000a6:loach:k3:1";

    public PropertyAccessoryFlags() {
        super(IID, new PropertyType(TYPE), new Access(false, true, true), DataFormat.UINT32);

        super.description().put("zh-CN", "附件标志");

        super.constraintValue(new ValueRange<>(DataFormat.UINT32, 0, 65535, 1));

    }

}