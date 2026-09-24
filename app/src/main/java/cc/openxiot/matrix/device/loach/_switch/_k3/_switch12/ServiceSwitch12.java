package cc.openxiot.matrix.device.loach._switch._k3._switch12;

import cc.openxiot.matrix.device.loach._switch._k3._switch12.properties.PropertyOn;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: 右键
 */
public class ServiceSwitch12 extends ServiceController {

    public static final int IID = 12;
    public static final String TYPE = "urn:homekit-spec:service:switch:00000049:loach:k3:1";

    public ServiceSwitch12() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "右键");

        super.properties().put(PropertyOn.IID, new PropertyOn());


    }

    /**
     * Property: 开关
     */
    public PropertyOn _property_on() {
        return (PropertyOn) properties().get(PropertyOn.IID);
    }

}
