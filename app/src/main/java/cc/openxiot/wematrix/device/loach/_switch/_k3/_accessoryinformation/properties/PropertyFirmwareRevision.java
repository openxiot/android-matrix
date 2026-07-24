package cc.openxiot.wematrix.device.loach._switch._k3._accessoryinformation.properties;

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
 * Property: 固件版本
 */
public class PropertyFirmwareRevision extends PropertyController<String> {

    public static final int IID = 2;
    public static final String TYPE = "urn:homekit-spec:property:firmware-revision:00000052:loach:k3:1";

    public PropertyFirmwareRevision() {
        super(IID, new PropertyType(TYPE), new Access(false, true, false), DataFormat.STRING);

        super.description().put("zh-CN", "固件版本");


    }

}