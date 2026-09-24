package cc.openxiot.matrix.device.loach._sensor._co2._carbondioxidesensor;

import cc.openxiot.matrix.device.loach._sensor._co2._carbondioxidesensor.properties.PropertyCarbonDioxideDetected;

import cn.geekcity.xiot.spec.definition.urn.ServiceType;
import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.ServiceController;

/**
 * Service: 二氧化碳传感器
 */
public class ServiceCarbonDioxideSensor extends ServiceController {

    public static final int IID = 11;
    public static final String TYPE = "urn:homekit-spec:service:carbon-dioxide-sensor:00000097:loach:co2:1";

    public ServiceCarbonDioxideSensor() {
        super(IID, new ServiceType(TYPE));

        super.description().put("zh-CN", "二氧化碳传感器");

        super.properties().put(PropertyCarbonDioxideDetected.IID, new PropertyCarbonDioxideDetected());


    }

    /**
     * Property: 检测到CO2
     */
    public PropertyCarbonDioxideDetected _property_carbon_dioxide_detected() {
        return (PropertyCarbonDioxideDetected) properties().get(PropertyCarbonDioxideDetected.IID);
    }

}
