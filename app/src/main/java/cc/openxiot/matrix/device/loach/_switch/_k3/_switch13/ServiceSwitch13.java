package cc.openxiot.matrix.device.loach._switch._k3._switch13;

import cc.openxiot.matrix.device.loach._switch._k3._switch13.properties.PropertyOn;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: Middle Switch
 */
public class ServiceSwitch13 extends ServiceController {

    public static final int IID = 13;
    public static final String TYPE = "urn:homekit-spec:service:switch:00000049:loach:k3:1";

    public ServiceSwitch13() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "Middle Switch");

        super.properties().put(PropertyOn.IID, new PropertyOn());


    }

    /**
     * Property: 开关
     */
    public PropertyOn _property_on() {
        return (PropertyOn) properties().get(PropertyOn.IID);
    }

}
