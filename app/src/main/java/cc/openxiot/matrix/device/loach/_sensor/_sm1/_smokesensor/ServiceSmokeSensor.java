package cc.openxiot.matrix.device.loach._sensor._sm1._smokesensor;

import cc.openxiot.matrix.device.loach._sensor._sm1._smokesensor.properties.PropertySmokeDetected;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: 烟雾传感器
 */
public class ServiceSmokeSensor extends ServiceController {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:service:smoke-sensor:00000087:loach:sm1:1";

    public ServiceSmokeSensor() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "烟雾传感器");

        super.properties().put(PropertySmokeDetected.IID, new PropertySmokeDetected());


    }

    /**
     * Property: 检测到烟雾
     */
    public PropertySmokeDetected _property_smoke_detected() {
        return (PropertySmokeDetected) properties().get(PropertySmokeDetected.IID);
    }

}
