package cc.openxiot.wematrix.device.loach._switch._k3._switch11;

import cc.openxiot.wematrix.device.loach._switch._k3._switch11.properties.PropertyOn;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: 左键
 */
public class ServiceSwitch11 extends ServiceController {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:service:switch:00000049:loach:k3:1";

    public ServiceSwitch11() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "左键");

        super.properties().put(PropertyOn.IID, new PropertyOn());


    }

    /**
     * Property: 开关
     */
    public PropertyOn _property_on() {
        return (PropertyOn) properties().get(PropertyOn.IID);
    }

}
