package cc.openxiot.matrix.device.loach._sensor._l1._lightsensor;

import cc.openxiot.matrix.device.loach._sensor._l1._lightsensor.properties.PropertyCurrentAmbientLightLevel;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: 光线传感器
 */
public class ServiceLightSensor extends ServiceController {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:service:light-sensor:00000084:loach:l1:1";

    public ServiceLightSensor() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "光线传感器");

        super.properties().put(PropertyCurrentAmbientLightLevel.IID, new PropertyCurrentAmbientLightLevel());


    }

    /**
     * Property: 光照强度
     */
    public PropertyCurrentAmbientLightLevel _property_current_ambient_light_level() {
        return (PropertyCurrentAmbientLightLevel) properties().get(PropertyCurrentAmbientLightLevel.IID);
    }

}
